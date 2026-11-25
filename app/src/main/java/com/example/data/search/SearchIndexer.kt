package com.example.data.search

import android.content.Context
import android.net.Uri
import com.example.data.search.db.IndexStatus
import com.example.data.text.OpenFailure
import com.example.data.text.OpenResult
import com.example.data.text.PdfTextEngine
import com.example.data.text.TextQuality
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

class SearchIndexer(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val repo: SearchIndexRepository,
    private val textEngine: PdfTextEngine,
    private val libraryCandidates: Flow<List<String>> // uris, most recently opened first
) {
    private val _pauseReason = MutableStateFlow<PauseReason?>(null)
    val pauseReason: StateFlow<PauseReason?> = _pauseReason.asStateFlow()

    private val queueMutex = Mutex()
    private val queue = ArrayDeque<String>()
    private var currentUri: String? = null
    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    private val started = AtomicBoolean(false)
    private var workerJob: Job? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return

        scope.launch {
            delay(IndexConfig.START_DELAY_MS)
            libraryCandidates.collect { uris ->
                repo.pruneTo(uris.toSet())
                enqueuePending(uris)
            }
        }

        launchWorker()
    }

    private fun launchWorker() {
        workerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val nextUri = queueMutex.withLock {
                    if (queue.isEmpty()) {
                        currentUri = null
                        null
                    } else {
                        queue.removeFirst().also { currentUri = it }
                    }
                }
                if (nextUri == null) {
                    wakeSignal.receive()
                    continue
                }
                try {
                    indexSingleDocument(nextUri)
                } finally {
                    queueMutex.withLock {
                        if (currentUri == nextUri) {
                            currentUri = null
                        }
                    }
                }
            }
        }
    }

    fun prioritize(uri: String) {
        if (uri.isBlank()) return
        scope.launch {
            var enqueued = false
            queueMutex.withLock {
                if (currentUri == uri) return@withLock
                queue.remove(uri)
                queue.addFirst(uri)
                enqueued = true
            }
            if (enqueued) {
                wakeSignal.trySend(Unit)
            }
        }
    }

    suspend fun rebuildAll() {
        queueMutex.withLock {
            queue.clear()
        }
        workerJob?.cancel()
        workerJob?.join()
        _pauseReason.value = null
        repo.clearAll()
        if (started.get()) {
            launchWorker()
        }
        val candidates = runCatching { libraryCandidates.first() }.getOrDefault(emptyList())
        enqueuePending(candidates)
    }

    private suspend fun enqueuePending(uris: List<String>) {
        if (uris.isEmpty()) return
        val docsByUri = repo.getAllDocs().associateBy { it.uriString }
        var addedAny = false
        queueMutex.withLock {
            for (uri in uris) {
                val existingStatus = docsByUri[uri]?.status
                val isTerminal = existingStatus == IndexStatus.DONE ||
                    existingStatus == IndexStatus.FAILED ||
                    existingStatus == IndexStatus.ENCRYPTED
                if (!isTerminal && uri != currentUri && uri !in queue) {
                    queue.addLast(uri)
                    addedAny = true
                }
            }
        }
        if (addedAny) {
            wakeSignal.trySend(Unit)
        }
    }

    internal suspend fun indexSingleDocument(uri: String) {
        var docId: Long? = null
        try {
            val parsedUri = Uri.parse(uri)
            val fp = FileFingerprint.of(appContext, parsedUri) ?: return
            val doc = repo.getOrCreate(uri, fp)
            docId = doc.docId

            if (
                doc.status == IndexStatus.DONE ||
                doc.status == IndexStatus.FAILED ||
                doc.status == IndexStatus.ENCRYPTED
            ) {
                return
            }

            waitForPolicyIfNeeded()

            when (val openRes = textEngine.open(parsedUri)) {
                is OpenResult.Error -> {
                    val terminal = if (openRes.failure == OpenFailure.ENCRYPTED) {
                        IndexStatus.ENCRYPTED
                    } else {
                        IndexStatus.FAILED
                    }
                    repo.setStatus(doc.docId, terminal)
                    return
                }

                is OpenResult.Ok -> {
                    openRes.session.use { session ->
                        val pageCount = session.pageCount
                        val cappedTotal = minOf(pageCount, IndexConfig.MAX_PAGES_PER_DOC)
                        repo.setPageCount(doc.docId, pageCount)
                        repo.setStatus(doc.docId, IndexStatus.INDEXING)

                        if (doc.nextPage >= cappedTotal) {
                            repo.setStatus(doc.docId, IndexStatus.DONE)
                            return
                        }

                        val batch = ArrayList<Pair<Int, String>>(IndexConfig.BATCH_PAGES)
                        var processedInBatch = 0
                        var lastProcessedPage = doc.nextPage - 1

                        session.pages(from = doc.nextPage)
                            .takeWhile { it.pageIndex < cappedTotal }
                            .collect { pageText ->
                                if (pageText.quality == TextQuality.OK) {
                                    val normalized = SearchTextNormalizer.forIndex(pageText.text)
                                    if (normalized.isNotBlank()) {
                                        batch.add(pageText.pageIndex to normalized)
                                    }
                                }
                                processedInBatch++
                                lastProcessedPage = pageText.pageIndex

                                if (processedInBatch >= IndexConfig.BATCH_PAGES) {
                                    waitForPolicyIfNeeded()
                                    repo.writeTextBatch(
                                        docId = doc.docId,
                                        pages = batch.toList(),
                                        nextPage = pageText.pageIndex + 1
                                    )
                                    batch.clear()
                                    processedInBatch = 0
                                    yield()
                                }
                            }

                        if (processedInBatch > 0) {
                            waitForPolicyIfNeeded()
                            repo.writeTextBatch(
                                docId = doc.docId,
                                pages = batch.toList(),
                                nextPage = (lastProcessedPage + 1).coerceAtLeast(cappedTotal)
                            )
                        }
                        repo.setStatus(doc.docId, IndexStatus.DONE)
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            val id = docId
            if (id != null) {
                runCatching { repo.setStatus(id, IndexStatus.FAILED) }
            }
        }
    }

    private suspend fun waitForPolicyIfNeeded() {
        while (true) {
            val reason = IndexPolicy.canRunNow(appContext)
            if (reason == null) {
                if (_pauseReason.value != null) {
                    _pauseReason.value = null
                }
                return
            }
            _pauseReason.value = reason
            delay(IndexConfig.PAUSE_RECHECK_MS)
        }
    }
}

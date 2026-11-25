package com.example.data.model

/**
 * Pure-Kotlin canonical folder key helpers stored in the existing
 * `PdfDocumentEntity.folderName` column (preserving Room schema & user data).
 *
 * Key conventions:
 * - Virtual folders: `"vf:<name>"` (e.g. `"vf:Imported"` or user-created `"vf:Exam Prep"`).
 * - Device folders: normalized forward-slash directory path
 *   (e.g. `"/storage/emulated/0/Download"` or `"/storage/emulated/0/Telegram/Download"`),
 *   keeping same-named directories at different paths distinct.
 */
object FolderKey {
    const val VIRTUAL_PREFIX = "vf:"
    const val IMPORTED = "vf:Imported"

    private val MULTIPLE_SLASHES = Regex("/+")

    fun isVirtual(raw: String?): Boolean =
        raw?.trim()?.startsWith(VIRTUAL_PREFIX) == true

    fun virtual(name: String): String {
        val clean = name.trim().removePrefix(VIRTUAL_PREFIX).trim()
        return if (clean.isEmpty()) IMPORTED else "$VIRTUAL_PREFIX$clean"
    }

    fun device(parentPath: String?): String {
        val normalized = parentPath?.trim().orEmpty()
            .replace('\\', '/')
            .replace(MULTIPLE_SLASHES, "/")
        if (normalized.isBlank()) return IMPORTED
        val withoutTrailing = if (normalized.length > 1) normalized.trimEnd('/') else normalized
        if (withoutTrailing.isBlank() || withoutTrailing == "/") return IMPORTED
        return withoutTrailing
    }

    fun canonical(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return IMPORTED
        if (trimmed.startsWith(VIRTUAL_PREFIX)) {
            return virtual(trimmed.removePrefix(VIRTUAL_PREFIX))
        }
        return device(trimmed)
    }

    fun displayName(key: String, importedLabel: String = "Imported"): String {
        val canon = canonical(key)
        if (canon == IMPORTED) return importedLabel
        if (canon.startsWith(VIRTUAL_PREFIX)) {
            return canon.removePrefix(VIRTUAL_PREFIX)
        }
        val leaf = canon.substringAfterLast('/').trim()
        return leaf.ifBlank { importedLabel }
    }

    fun displayNames(
        keys: Collection<String>,
        importedLabel: String = "Imported"
    ): Map<String, String> {
        val canonicalKeys = keys.map { canonical(it) }.distinct()
        val baseByKey = canonicalKeys.associateWith { displayName(it, importedLabel) }
        val collisions = canonicalKeys.groupBy { baseByKey.getValue(it).lowercase() }

        val result = LinkedHashMap<String, String>(canonicalKeys.size)
        for ((_, group) in collisions) {
            if (group.size <= 1) {
                val single = group.first()
                result[single] = baseByKey.getValue(single)
            } else {
                for (key in group) {
                    val base = baseByKey.getValue(key)
                    val qualifier = parentQualifier(key)
                    result[key] = if (qualifier.isNotBlank()) "$base ($qualifier)" else base
                }
            }
        }
        return result
    }

    private fun parentQualifier(canonicalKey: String): String {
        if (canonicalKey.startsWith(VIRTUAL_PREFIX)) return "custom"
        val parent = canonicalKey.substringBeforeLast('/', "").trimEnd('/')
        if (parent.isBlank()) return ""
        val seg = parent.substringAfterLast('/')
        return if (seg == "0" || seg.equals("emulated", ignoreCase = true)) {
            "Storage"
        } else {
            seg
        }
    }
}

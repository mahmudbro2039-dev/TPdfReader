package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.AnnotationType

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["uriString"],
            childColumns = ["documentUri"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentUri"])]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val documentUri: String,
    val documentTitle: String,
    val pageIndex: Int,
    val type: String = AnnotationType.HIGHLIGHT.name,
    val quotedText: String = "",
    val noteText: String = "",
    val colorHex: String = "#FACC15",
    /**
     * Serialized vector strokes for handwritten signatures ("x1,y1;x2,y2|x1,y1;x2,y2")
     * normalized to 0..1 coordinates so vector signatures render sharply at any resolution.
     */
    val signatureVectorData: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val annotationType: AnnotationType
        get() = AnnotationType.fromName(type)
}

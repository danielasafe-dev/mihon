package eu.kanade.tachiyomi.ui.reader.translation

import kotlinx.serialization.Serializable

@Serializable
data class ReaderTranslationResult(
    val sourceLanguage: String,
    val targetLanguage: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val blocks: List<ReaderTranslationBlock>,
)

@Serializable
data class ReaderTranslationBlock(
    val sourceText: String,
    val translatedText: String,
    val bounds: ReaderTranslationBounds,
)

@Serializable
data class ReaderTranslationBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class ReaderOcrResult(
    val imageWidth: Int,
    val imageHeight: Int,
    val blocks: List<ReaderOcrBlock>,
)

data class ReaderOcrBlock(
    val text: String,
    val bounds: ReaderTranslationBounds,
)

internal const val READER_TRANSLATION_SOURCE_LANGUAGE = "en"
internal const val READER_TRANSLATION_TARGET_LANGUAGE = "pt"
internal const val READER_TRANSLATION_CACHE_VERSION = "2"

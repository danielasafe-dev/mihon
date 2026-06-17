package eu.kanade.tachiyomi.ui.reader.translation

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

data class ReaderTranslationPageKey(
    val raw: String,
) {
    companion object {
        fun from(page: ReaderPage): ReaderTranslationPageKey {
            val chapter = page.chapter.chapter
            val mangaId = chapter.manga_id?.toString().orEmpty()
            val chapterId = chapter.id?.toString() ?: chapter.url
            val imageIdentity = page.imageUrl ?: page.url

            return ReaderTranslationPageKey(
                raw = listOf(
                    mangaId,
                    chapterId,
                    page.index.toString(),
                    imageIdentity,
                    READER_TRANSLATION_SOURCE_LANGUAGE,
                    READER_TRANSLATION_TARGET_LANGUAGE,
                ).joinToString(separator = "|"),
            )
        }
    }
}

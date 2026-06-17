package eu.kanade.tachiyomi.ui.reader.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MlKitReaderTranslator {
    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.PORTUGUESE)
            .build(),
    )
    private val downloadMutex = Mutex()

    @Volatile
    private var modelReady = false

    suspend fun translate(blocks: List<ReaderOcrBlock>): List<ReaderTranslationBlock> {
        if (blocks.isEmpty()) return emptyList()

        ensureModelDownloaded()

        return blocks.mapNotNull { block ->
            val translatedText = translator.translate(block.text).await().trim()
            if (translatedText.isBlank()) return@mapNotNull null

            ReaderTranslationBlock(
                sourceText = block.text,
                translatedText = translatedText,
                bounds = block.bounds,
            )
        }
    }

    private suspend fun ensureModelDownloaded() {
        if (modelReady) return

        downloadMutex.withLock {
            if (modelReady) return

            translator.downloadModelIfNeeded(
                DownloadConditions.Builder().build(),
            ).await()
            modelReady = true
        }
    }
}

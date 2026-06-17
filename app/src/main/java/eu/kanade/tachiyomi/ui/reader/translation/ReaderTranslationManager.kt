package eu.kanade.tachiyomi.ui.reader.translation

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class ReaderTranslationManager(
    private val cache: ReaderTranslationCache,
    private val ocr: MlKitReaderOcr,
    private val translator: MlKitReaderTranslator,
    private val scope: CoroutineScope,
) {
    private val processingMutex = Mutex()
    private val inFlight = ConcurrentHashMap<String, Deferred<Result<ReaderTranslationResult>>>()

    suspend fun translate(page: ReaderPage): Result<ReaderTranslationResult> {
        val key = ReaderTranslationPageKey.from(page)
        cache.get(key)?.let { return Result.success(it) }

        val deferred = inFlight.getOrPut(key.raw) {
            scope.async {
                runCatching {
                    processingMutex.withLock {
                        cache.get(key)?.let { return@withLock it }

                        val streamProvider = page.stream ?: error("Page image stream is not ready")
                        val ocrResult = ocr.recognize(streamProvider)
                        val translatedBlocks = translator.translate(ocrResult.blocks)
                        val result = ReaderTranslationResult(
                            sourceLanguage = READER_TRANSLATION_SOURCE_LANGUAGE,
                            targetLanguage = READER_TRANSLATION_TARGET_LANGUAGE,
                            imageWidth = ocrResult.imageWidth,
                            imageHeight = ocrResult.imageHeight,
                            blocks = translatedBlocks,
                        )

                        cache.put(key, result)
                        result
                    }
                }.also {
                    inFlight.remove(key.raw)
                }
            }
        }

        return deferred.await()
    }
}

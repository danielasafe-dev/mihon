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

        return groupNearbyBlocks(blocks)
            .flatMap { group ->
                translateGroup(group)
            }
    }

    private suspend fun translateGroup(blocks: List<ReaderOcrBlock>): List<ReaderTranslationBlock> {
        if (blocks.size == 1) {
            return translateIndividually(blocks)
        }

        val groupedText = blocks.joinToString(TRANSLATION_CONTEXT_SEPARATOR) { it.text }
        val translatedLines = translator.translate(groupedText)
            .await()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (translatedLines.size != blocks.size) {
            return translateIndividually(blocks)
        }

        return blocks.zip(translatedLines).mapNotNull { (block, translatedText) ->
            if (translatedText.isBlank()) return@mapNotNull null

            ReaderTranslationBlock(
                sourceText = block.text,
                translatedText = translatedText,
                bounds = block.bounds,
            )
        }
    }

    private suspend fun translateIndividually(blocks: List<ReaderOcrBlock>): List<ReaderTranslationBlock> {
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

    private fun groupNearbyBlocks(blocks: List<ReaderOcrBlock>): List<List<ReaderOcrBlock>> {
        val sortedBlocks = blocks.sortedWith(
            compareBy<ReaderOcrBlock> { it.bounds.top }
                .thenBy { it.bounds.left },
        )

        val groups = mutableListOf<List<ReaderOcrBlock>>()
        var currentGroup = mutableListOf<ReaderOcrBlock>()
        var currentBounds: ReaderTranslationBounds? = null

        sortedBlocks.forEach { block ->
            val bounds = currentBounds
            if (
                currentGroup.isEmpty() ||
                (
                    currentGroup.size < MAX_TRANSLATION_CONTEXT_BLOCKS &&
                        bounds != null &&
                        shouldShareTranslationContext(bounds, block.bounds)
                    )
            ) {
                currentGroup.add(block)
                currentBounds = mergeBounds(currentBounds, block.bounds)
            } else {
                groups += currentGroup
                currentGroup = mutableListOf(block)
                currentBounds = block.bounds
            }
        }

        if (currentGroup.isNotEmpty()) {
            groups += currentGroup
        }

        return groups
    }

    private fun shouldShareTranslationContext(
        groupBounds: ReaderTranslationBounds,
        blockBounds: ReaderTranslationBounds,
    ): Boolean {
        val groupHeight = groupBounds.height.coerceAtLeast(1f)
        val blockHeight = blockBounds.height.coerceAtLeast(1f)
        val maxVerticalGap = maxOf(groupHeight, blockHeight) * MAX_VERTICAL_GAP_MULTIPLIER
        val verticalGap = blockBounds.top - groupBounds.bottom

        if (verticalGap < 0f) return true
        return verticalGap <= maxVerticalGap
    }

    private fun mergeBounds(
        current: ReaderTranslationBounds?,
        block: ReaderTranslationBounds,
    ): ReaderTranslationBounds {
        if (current == null) return block

        return ReaderTranslationBounds(
            left = minOf(current.left, block.left),
            top = minOf(current.top, block.top),
            right = maxOf(current.right, block.right),
            bottom = maxOf(current.bottom, block.bottom),
        )
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

private val ReaderTranslationBounds.height: Float
    get() = bottom - top

private const val TRANSLATION_CONTEXT_SEPARATOR = "\n"
private const val MAX_TRANSLATION_CONTEXT_BLOCKS = 4
private const val MAX_VERTICAL_GAP_MULTIPLIER = 1.5f

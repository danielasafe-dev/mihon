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
        val sourceText = blocks.joinToString(TRANSLATION_CONTEXT_SEPARATOR) { it.text.normalizeForTranslation() }
        val translatedText = translator.translate(sourceText).await().trim()
        if (translatedText.isBlank()) return emptyList()

        return listOf(
            ReaderTranslationBlock(
                sourceText = sourceText,
                translatedText = translatedText,
                bounds = blocks
                    .map { it.bounds }
                    .reduce(::mergeBounds),
            ),
        )
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
        val maxCenterGap = maxOf(groupBounds.width, blockBounds.width) * MAX_CENTER_GAP_MULTIPLIER
        val horizontalCenterGap = kotlin.math.abs(groupBounds.centerX - blockBounds.centerX)

        return verticalGap <= maxVerticalGap && horizontalCenterGap <= maxCenterGap
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

private fun String.normalizeForTranslation(): String {
    return lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

private val ReaderTranslationBounds.height: Float
    get() = bottom - top

private val ReaderTranslationBounds.width: Float
    get() = right - left

private val ReaderTranslationBounds.centerX: Float
    get() = left + width / 2f

private const val TRANSLATION_CONTEXT_SEPARATOR = "\n"
private const val MAX_TRANSLATION_CONTEXT_BLOCKS = 4
private const val MAX_VERTICAL_GAP_MULTIPLIER = 1.5f
private const val MAX_CENTER_GAP_MULTIPLIER = 0.85f

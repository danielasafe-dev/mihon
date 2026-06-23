package eu.kanade.tachiyomi.ui.reader.translation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream

class MlKitReaderOcr {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(streamProvider: () -> InputStream): ReaderOcrResult = withContext(Dispatchers.Default) {
        val bitmap = decodeForOcr(streamProvider)
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val text = recognizer.process(image).await()

            ReaderOcrResult(
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                blocks = text.textBlocks.flatMap { block ->
                    if (block.lines.isNotEmpty()) {
                        block.lines.mapNotNull { line -> line.toOcrBlock() }
                    } else {
                        listOfNotNull(block.toOcrBlock())
                    }
                }.sortedWith(
                    compareBy<ReaderOcrBlock> { it.bounds.top }
                        .thenBy { it.bounds.left },
                ),
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeForOcr(streamProvider: () -> InputStream): Bitmap {
        val bytes = streamProvider().use { it.readBytes() }
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, boundsOptions)

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateSampleSize(
                width = boundsOptions.outWidth,
                height = boundsOptions.outHeight,
            )
        }

        return BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, decodeOptions)
            ?: error("Unable to decode page image for OCR")
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1

        var sampleSize = 1
        while (
            (width / sampleSize) * (height / sampleSize) > MAX_OCR_PIXELS &&
            width / sampleSize > MIN_OCR_WIDTH
        ) {
            sampleSize *= 2
        }

        return sampleSize
    }
}

private fun com.google.mlkit.vision.text.Text.TextBlock.toOcrBlock(): ReaderOcrBlock? {
    return toOcrBlock(text, boundingBox)
}

private fun com.google.mlkit.vision.text.Text.Line.toOcrBlock(): ReaderOcrBlock? {
    return toOcrBlock(text, boundingBox)
}

private fun toOcrBlock(text: String, rect: Rect?): ReaderOcrBlock? {
    val bounds = rect ?: return null
    val sourceText = text.trim()
    if (sourceText.isBlank()) return null

    return ReaderOcrBlock(
        text = sourceText,
        bounds = ReaderTranslationBounds(
            left = bounds.left.toFloat(),
            top = bounds.top.toFloat(),
            right = bounds.right.toFloat(),
            bottom = bounds.bottom.toFloat(),
        ),
    )
}

private const val MAX_OCR_PIXELS = 10_000_000
private const val MIN_OCR_WIDTH = 900

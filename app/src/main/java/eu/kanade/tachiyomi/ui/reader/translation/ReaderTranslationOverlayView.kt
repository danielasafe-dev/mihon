package eu.kanade.tachiyomi.ui.reader.translation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.util.system.dpToPx
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import kotlin.math.max
import kotlin.math.min

class ReaderTranslationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val statusTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13.dpToPx.toFloat()
    }

    private var result: ReaderTranslationResult? = null
    private var statusText: String? = null

    fun showPreparing() {
        result = null
        statusText = context.stringResource(MR.strings.reader_translation_preparing)
        isVisible = true
        invalidate()
    }

    fun showResult(result: ReaderTranslationResult) {
        this.result = result
        statusText = null
        isVisible = result.blocks.isNotEmpty()
        invalidate()
    }

    fun showError() {
        result = null
        statusText = context.stringResource(MR.strings.reader_translation_failed)
        isVisible = true
        invalidate()
    }

    fun clear() {
        result = null
        statusText = null
        isVisible = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentResult = result
        if (currentResult != null) {
            drawTranslations(canvas, currentResult)
        }

        statusText?.let { drawStatus(canvas, it) }
    }

    private fun drawTranslations(canvas: Canvas, result: ReaderTranslationResult) {
        if (result.imageWidth <= 0 || result.imageHeight <= 0) return

        val scaleX = width.toFloat() / result.imageWidth.toFloat()
        val scaleY = height.toFloat() / result.imageHeight.toFloat()
        val padding = 4.dpToPx.toFloat()
        val radius = 4.dpToPx.toFloat()

        result.blocks.forEach { block ->
            val rect = RectF(
                block.bounds.left * scaleX,
                block.bounds.top * scaleY,
                block.bounds.right * scaleX,
                block.bounds.bottom * scaleY,
            ).apply {
                val horizontalPadding = max(padding * 2, width() * 0.24f)
                val verticalPadding = max(padding, height() * 0.18f)
                inset(-horizontalPadding, -verticalPadding)
                left = left.coerceIn(0f, width.toFloat())
                top = top.coerceIn(0f, height.toFloat())
                right = right.coerceIn(0f, width.toFloat())
                bottom = bottom.coerceIn(0f, height.toFloat())
            }

            if (rect.width() <= padding * 2 || rect.height() <= padding * 2) return@forEach

            textPaint.textSize = estimateTextSize(rect)
            val layoutWidth = max(1, (rect.width() - padding * 2).toInt())
            val layout = StaticLayout.Builder
                .obtain(block.translatedText, 0, block.translatedText.length, textPaint, layoutWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()

            val desiredHeight = layout.height + padding * 2
            if (desiredHeight > rect.height()) {
                rect.bottom = min(height.toFloat(), rect.top + desiredHeight)
            }

            canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
            canvas.save()
            canvas.translate(rect.left + padding, rect.top + max(padding, (rect.height() - layout.height) / 2f))
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun estimateTextSize(rect: RectF): Float {
        val heightBased = rect.height() * 0.28f
        return heightBased.coerceIn(11.dpToPx.toFloat(), 20.dpToPx.toFloat())
    }

    private fun drawStatus(canvas: Canvas, text: String) {
        val paddingX = 10.dpToPx.toFloat()
        val paddingY = 6.dpToPx.toFloat()
        val top = 12.dpToPx.toFloat()
        val left = 12.dpToPx.toFloat()
        val textWidth = statusTextPaint.measureText(text)
        val rect = RectF(
            left,
            top,
            min(width - left, left + textWidth + paddingX * 2),
            top + statusTextPaint.textSize + paddingY * 2,
        )

        canvas.drawRoundRect(rect, 6.dpToPx.toFloat(), 6.dpToPx.toFloat(), statusPaint)
        canvas.drawText(text, rect.left + paddingX, rect.bottom - paddingY - 2.dpToPx, statusTextPaint)
    }
}

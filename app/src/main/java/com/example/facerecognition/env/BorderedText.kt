package com.example.facerecognition.env

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Rect
import android.graphics.Typeface
import java.util.*


/**
 * Creates a left-aligned bordered text object with a white interior, and a black exterior with
 * the specified text size.
 *
 * @param textSize text size in pixels
 */
class BorderedText(private var textSize: Float = 0f) {
    private var interiorPaint: Paint? = null
    private var exteriorPaint: Paint? = null

    /**
     * Create a bordered text object with the specified interior and exterior colors, text size and
     * alignment.
     *
     * @param interiorColor the interior text color
     * @param exteriorColor the exterior text color
     * @param textSize text size in pixels
     */
    constructor(interiorColor: Int, exteriorColor: Int, textSize: Float) : this() {
        this(textSize)
        interiorPaint = Paint().apply {
            this.textSize = textSize
            color = interiorColor
            style = Paint.Style.FILL
            isAntiAlias = false
            alpha = 255
        }
        exteriorPaint = Paint().apply {
            this.textSize = textSize
            color = exteriorColor
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = textSize / 8
            isAntiAlias = false
            alpha = 255
        }
    }

    private operator fun invoke(textSize: Float) {

    }

    fun setTypeface(typeface: Typeface?) {
        interiorPaint?.typeface = typeface
        exteriorPaint?.typeface = typeface
    }

    fun drawText(canvas: Canvas, posX: Float, posY: Float, text: String?) {
        if (text != null) {
            exteriorPaint?.let { canvas.drawText(text, posX, posY, it) }
        }
        if (text != null) {
            interiorPaint?.let { canvas.drawText(text, posX, posY, it) }
        }
    }

    fun drawText(
        canvas: Canvas, posX: Float, posY: Float, text: String?, bgPaint: Paint?
    ) {
        val width: Float = exteriorPaint?.measureText(text) ?: 0f
        val textSize: Float = exteriorPaint?.textSize ?: 0f
        val paint = Paint(bgPaint)
        paint.style = Paint.Style.FILL
        paint.alpha = 160
        canvas.drawRect(posX, posY + textSize.toInt(), posX + width.toInt(), posY, paint)
        if (text != null) {
            interiorPaint?.let { canvas.drawText(text, posX, posY + textSize, it) }
        }
    }

    fun drawLines(canvas: Canvas, posX: Float, posY: Float, lines: Vector<String?>) {
        for ((lineNum, line) in lines.withIndex()) {
            drawText(canvas, posX, posY - getTextSize() * (lines.size - lineNum - 1), line)
        }
    }

    fun setInteriorColor(color: Int) {
        interiorPaint?.color = color
    }

    fun setExteriorColor(color: Int) {
        exteriorPaint?.color = color
    }

    fun getTextSize(): Float {
        return textSize
    }

    fun setAlpha(alpha: Int) {
        interiorPaint?.alpha = alpha
        exteriorPaint?.alpha = alpha
    }

    fun getTextBounds(
        line: String?, index: Int, count: Int, lineBounds: Rect?
    ) {
        interiorPaint?.getTextBounds(line, index, count, lineBounds)
    }

    fun setTextAlign(align: Align?) {
        interiorPaint?.textAlign = align
        exteriorPaint?.textAlign = align
    }
}
package com.example.facerecognition.customview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.example.facerecognition.tflite.SimilarityClassifier.Recognition


class RecognitionScoreView(context: Context, attrs: AttributeSet) : View(context, attrs),
    ResultsView {

    private val TEXT_SIZE_DIP = 14f
    private var textSizePx = 0f
    private var fgPaint: Paint
    private var bgPaint: Paint
    private var results: List<Recognition?>? = null

    init {
        textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            TEXT_SIZE_DIP,
            resources.displayMetrics
        )
        fgPaint = Paint()
        fgPaint.textSize = textSizePx
        bgPaint = Paint()
        bgPaint.color = -0x33bd7a0c
    }

    override fun setResults(results: List<Recognition?>?) {
        this.results = results
        postInvalidate();
    }

    override fun onDraw(canvas: Canvas) {
        val x = 10
        var y = (fgPaint.textSize * 1.5f).toInt()
        canvas.drawPaint(bgPaint)
        if (results != null) {
            for (recognise in results!!) {
                canvas.drawText(
                    recognise?.getTitle() + ": " + recognise?.distance, x.toFloat(),
                    y.toFloat(), fgPaint
                )
                y += (fgPaint.textSize * 1.5f).toInt()
            }
        }
    }
}
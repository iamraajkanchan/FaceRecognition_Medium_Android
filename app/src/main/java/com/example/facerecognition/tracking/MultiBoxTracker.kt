package com.example.facerecognition.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.Paint.Cap
import android.graphics.Paint.Join
import android.text.TextUtils
import android.util.TypedValue
import com.example.facerecognition.env.BorderedText
import com.example.facerecognition.env.ImageUtils
import com.example.facerecognition.env.Logger
import com.example.facerecognition.tflite.SimilarityClassifier.Recognition
import java.util.*


class MultiBoxTracker(context: Context) {
    private val TEXT_SIZE_DIP = 18f
    private val MIN_SIZE = 16.0f
    private val COLORS = intArrayOf(
        Color.BLUE,
        Color.RED,
        Color.GREEN,
        Color.YELLOW,
        Color.CYAN,
        Color.MAGENTA,
        Color.WHITE,
        Color.parseColor("#55FF55"),
        Color.parseColor("#FFA500"),
        Color.parseColor("#FF8888"),
        Color.parseColor("#AAAAFF"),
        Color.parseColor("#FFFFAA"),
        Color.parseColor("#55AAAA"),
        Color.parseColor("#AA33AA"),
        Color.parseColor("#0D0068")
    )
    private val screenRects: MutableList<Pair<Float?, RectF>> = mutableListOf()
    private val logger: Logger = Logger()
    private val availableColors: Queue<Int> = LinkedList<Int>()
    private val trackedObjects: MutableList<TrackedRecognition> = LinkedList<TrackedRecognition>()
    private val boxPaint: Paint = Paint()
    private var textSizePx = 0f
    private var borderedText: BorderedText? = null
    private var frameToCanvasMatrix: Matrix? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private var sensorOrientation = 0

    init {
        for (color in COLORS) {
            availableColors.add(color)
        }
        boxPaint.color = Color.RED
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = 10.0f
        boxPaint.strokeCap = Cap.ROUND
        boxPaint.strokeJoin = Join.ROUND
        boxPaint.strokeMiter = 100f
        textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.resources.displayMetrics
        )
        borderedText = BorderedText(Color.WHITE, Color.BLACK, textSizePx)
    }

    @Synchronized
    fun setFrameConfiguration(
        width: Int, height: Int, sensorOrientation: Int
    ) {
        frameWidth = width
        frameHeight = height
        this.sensorOrientation = sensorOrientation
    }

    @Synchronized
    fun drawDebug(canvas: Canvas) {
        val textPaint = Paint()
        textPaint.color = Color.WHITE
        textPaint.textSize = 60.0f
        val boxPaint = Paint()
        boxPaint.color = Color.RED
        boxPaint.alpha = 200
        boxPaint.style = Paint.Style.STROKE
        for (detection: Pair<Float?, RectF> in screenRects) {
            val rect: RectF = detection.second
            canvas.drawRect(rect, boxPaint)
            canvas.drawText("" + detection.first, rect.left, rect.top, textPaint)
            borderedText!!.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first)
        }
    }

    @Synchronized
    fun trackResults(results: List<Recognition>, timestamp: Long) {
        logger.i("Processing %d results from %d", results.size, timestamp)
        processResults(results)
    }

    private fun getFrameToCanvasMatrix(): Matrix? {
        return frameToCanvasMatrix
    }

    @Synchronized
    fun draw(canvas: Canvas) {
        val rotated = sensorOrientation % 180 == 90
        val multiplier: Float =
            (canvas.height / (if (rotated) frameWidth else frameHeight).toFloat()).coerceAtMost(
                canvas.width / (if (rotated) frameHeight else frameWidth).toFloat()
            )
        frameToCanvasMatrix = ImageUtils.getTransformationMatrix(
            frameWidth,
            frameHeight,
            (multiplier * if (rotated) frameHeight else frameWidth).toInt(),
            (multiplier * if (rotated) frameWidth else frameHeight).toInt(),
            sensorOrientation,
            false
        )
        for (recognition in trackedObjects) {
            val trackedPos = RectF(recognition.location)
            getFrameToCanvasMatrix()?.mapRect(trackedPos)
            boxPaint.color = recognition.color
            val cornerSize = trackedPos.width().coerceAtMost(trackedPos.height()) / 8.0f
            canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint)
            @SuppressLint("DefaultLocale") val strConfidence =
                if (recognition.detectionConfidence < 0) "" else String.format(
                    "%.2f",
                    recognition.detectionConfidence
                ) + ""
            val labelString = if (!TextUtils.isEmpty(recognition.title)) String.format(
                "%s %s",
                recognition.title,
                strConfidence
            ) else strConfidence
            borderedText!!.drawText(
                canvas, trackedPos.left + cornerSize, trackedPos.top, labelString, boxPaint
            )
        }
    }

    private fun processResults(results: List<Recognition>) {
        val rectsToTrack: MutableList<Pair<Float?, Recognition>> =
            LinkedList<Pair<Float?, Recognition>>()
        screenRects.clear()
        val rgbFrameToScreen = Matrix(getFrameToCanvasMatrix())
        for (result in results) {
            val detectionFrameRect = RectF(result.getLocation())
            val detectionScreenRect = RectF()
            rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect)
            logger.v(
                "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect
            )
            screenRects.add(Pair(result.distance, detectionScreenRect))
            if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
                logger.w("Degenerate rectangle! $detectionFrameRect")
                continue
            }
            rectsToTrack.add(Pair(result.distance, result))
        }
        trackedObjects.clear()
        if (rectsToTrack.isEmpty()) {
            logger.v("Nothing to track, aborting.")
            return
        }
        for (potential: Pair<Float?, Recognition> in rectsToTrack) {
            val trackedRecognition = TrackedRecognition()
            trackedRecognition.detectionConfidence = potential.first!!
            trackedRecognition.location = RectF(potential.second.getLocation())
            trackedRecognition.title = potential.second.getTitle()
            if (potential.second.color != null) {
                trackedRecognition.color = potential.second.color!!
            } else {
                trackedRecognition.color = COLORS[trackedObjects.size]
            }
            trackedObjects.add(trackedRecognition)
            if (trackedObjects.size >= COLORS.size) {
                break
            }
        }
    }

    private class TrackedRecognition {
        var location: RectF? = null
        var detectionConfidence = 0f
        var color = 0
        var title: String? = null
    }
}
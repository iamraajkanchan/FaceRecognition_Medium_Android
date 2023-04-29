package com.example.facerecognition

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.*
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.facerecognition.customview.OverlayView
import com.example.facerecognition.env.BorderedText
import com.example.facerecognition.env.ImageUtils
import com.example.facerecognition.env.Logger
import com.example.facerecognition.tflite.SimilarityClassifier
import com.example.facerecognition.tflite.TFLiteObjectDetectionAPIModel
import com.example.facerecognition.tracking.MultiBoxTracker
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.IOException
import java.util.*

class DetectorActivity : CameraActivity() {

    companion object {
        // FaceNet
        // private static final int TF_OD_API_INPUT_SIZE = 160;
        // private static final boolean TF_OD_API_IS_QUANTIZED = false;
        // private static final String TF_OD_API_MODEL_FILE = "facenet.tflite";
        // //private static final String TF_OD_API_MODEL_FILE = "facenet_hiroki.tflite";

        // FaceNet
        // private static final int TF_OD_API_INPUT_SIZE = 160;
        // private static final boolean TF_OD_API_IS_QUANTIZED = false;
        // private static final String TF_OD_API_MODEL_FILE = "facenet.tflite";
        // private static final String TF_OD_API_MODEL_FILE = "facenet_hiroki.tflite";

        // MobileFaceNet
        private const val TF_OD_API_INPUT_SIZE = 112
        private const val TF_OD_API_IS_QUANTIZED = false
        private const val TF_OD_API_MODEL_FILE = "mobile_face_net.tflite"
        private const val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt"
        private val MODE = DetectorMode.TF_OD_API

        // Minimum detection confidence to track a detection.
        private const val MINIMUM_CONFIDENCE_TF_OD_API = 0.5f
        private const val MAINTAIN_ASPECT = false

        private val DESIRED_PREVIEW_SIZE = Size(640, 480)

        //private static final int CROP_SIZE = 320;
        //private static final Size CROP_SIZE = new Size(320, 320);
        //private static final int CROP_SIZE = 320;
        //private static final Size CROP_SIZE = new Size(320, 320);
        private const val SAVE_PREVIEW_BITMAP = true
        private const val TEXT_SIZE_DIP = 10f
    }

    private val LOGGER: Logger = Logger()
    private var trackingOverlay: OverlayView? = null
    private var sensorOrientation: Int? = null
    private var detector: SimilarityClassifier? = null
    private var lastProcessingTimeMs: Long = 0
    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var cropCopyBitmap: Bitmap? = null
    private var computingDetection = false
    private var addPending = false

    //private boolean adding = false;
    private var timestamp: Long = 0
    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null

    //private Matrix cropToPortraitTransform;
    private var tracker: MultiBoxTracker? = null
    private var borderedText: BorderedText? = null

    // Face detector
    private var faceDetector: FaceDetector? = null

    // here the preview image is drawn in portrait way
    private var portraitBmp: Bitmap? = null

    // here the face is cropped and drawn
    private var faceBmp: Bitmap? = null
    private var fabAdd: FloatingActionButton? = null
    //private HashMap<String, Classifier.Recognition> knownFaces = new HashMap<>();

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fabAdd = findViewById(R.id.fab_add)
        fabAdd?.setOnClickListener { onAddClick() }

        // Real-time contour detection of multiple faces
        val options: FaceDetectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        val detector: FaceDetector = FaceDetection.getClient(options)
        faceDetector = detector
        //checkWritePermission();
    }


    private fun onAddClick() {
        addPending = true
    }

    override fun onPreviewSizeChosen(size: Size, rotation: Int) {
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics
        )
        borderedText = BorderedText(Color.WHITE, Color.BLACK, textSizePx)
        borderedText?.setTypeface(Typeface.MONOSPACE)
        tracker = MultiBoxTracker(this)
        try {
            detector = TFLiteObjectDetectionAPIModel.create(
                assets,
                TF_OD_API_MODEL_FILE,
                TF_OD_API_LABELS_FILE,
                TF_OD_API_INPUT_SIZE,
                TF_OD_API_IS_QUANTIZED
            )
            //cropSize = TF_OD_API_INPUT_SIZE;
        } catch (e: IOException) {
            e.printStackTrace()
            LOGGER.e(e, "Exception initializing classifier!")
            val toast = Toast.makeText(
                applicationContext, "Classifier could not be initialized", Toast.LENGTH_SHORT
            )
            toast.show()
            finish()
        }
        previewWidth = size.width
        previewHeight = size.height
        sensorOrientation = rotation - getScreenOrientation()
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation)
        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight)
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        val targetW: Int
        val targetH: Int
        if (sensorOrientation == 90 || sensorOrientation == 270) {
            targetH = previewWidth
            targetW = previewHeight
        } else {
            targetW = previewWidth
            targetH = previewHeight
        }
        val cropW = (targetW / 2.0).toInt()
        val cropH = (targetH / 2.0).toInt()
        croppedBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        portraitBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        faceBmp =
            Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888)
        frameToCropTransform = ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropW, cropH,
            sensorOrientation!!, MAINTAIN_ASPECT
        )

//    frameToCropTransform =
//            ImageUtils.getTransformationMatrix(
//                    previewWidth, previewHeight,
//                    previewWidth, previewHeight,
//                    sensorOrientation, MAINTAIN_ASPECT);
        cropToFrameTransform = Matrix()
        frameToCropTransform!!.invert(cropToFrameTransform)
        val frameToPortraitTransform: Matrix = ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight, targetW, targetH, sensorOrientation!!, MAINTAIN_ASPECT
        )
        trackingOverlay = findViewById(R.id.tracking_overlay)
        trackingOverlay?.addCallback(
            object : OverlayView.DrawCallback {
                override fun drawCallback(canvas: Canvas?) {
                    if (canvas != null) {
                        tracker!!.draw(canvas)
                    }
                    if (isDebug()) {
                        if (canvas != null) {
                            tracker!!.drawDebug(canvas)
                        }
                    }
                }
            })
        tracker?.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation!!)
    }


    override fun processImage() {
        ++timestamp
        val currTimestamp = timestamp
        trackingOverlay?.postInvalidate()

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage()
            return
        }
        computingDetection = true
        LOGGER.i("Preparing image $currTimestamp for detection in bg thread.")
        rgbFrameBitmap!!.setPixels(
            getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight
        )
        readyForNextImage()
        val canvas = Canvas(croppedBitmap!!)
        canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap!!)
        }
        val image: InputImage = InputImage.fromBitmap(croppedBitmap!!, 0)
        faceDetector
            ?.process(image)
            ?.addOnSuccessListener(OnSuccessListener<List<Face>> { faces ->
                if (faces.isEmpty()) {
                    updateResults(currTimestamp, LinkedList<SimilarityClassifier.Recognition>())
                    return@OnSuccessListener
                }
                runInBackground {
                    onFacesDetected(currTimestamp, faces, addPending)
                    addPending = false
                }
            })
    }

    override fun getLayoutId(): Int {
        return R.layout.fragment_camera_connection
    }

    override fun getDesiredPreviewFrameSize(): Size {
        return DESIRED_PREVIEW_SIZE
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum class DetectorMode {
        TF_OD_API
    }

    override fun setUseNNAPI(isChecked: Boolean) {
        runInBackground { detector?.setUseNNAPI(isChecked) }
    }

    override fun setNumThreads(numThreads: Int) {
        runInBackground { detector?.setNumThreads(numThreads) }
    }


    // Face Processing
    private fun createTransform(
        srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int, applyRotation: Int
    ): Matrix {
        val matrix = Matrix()
        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                LOGGER.w("Rotation of %d % 90 != 0", applyRotation)
            }
            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f)
            // Rotate around origin.
            matrix.postRotate(applyRotation.toFloat())
        }

//        // Account for the already applied rotation, if any, and then determine how
//        // much scaling is needed for each axis.
//        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;
//        final int inWidth = transpose ? srcHeight : srcWidth;
//        final int inHeight = transpose ? srcWidth : srcHeight;
        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f)
        }
        return matrix
    }

    private fun showAddFaceDialog(rec: SimilarityClassifier.Recognition) {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout: View = inflater.inflate(R.layout.image_edit_dialog, null)
        val ivFace = dialogLayout.findViewById<ImageView>(R.id.dlg_image)
        val tvTitle = dialogLayout.findViewById<TextView>(R.id.dlg_title)
        val etName = dialogLayout.findViewById<EditText>(R.id.dlg_input)
        tvTitle.text = "Add Face"
        ivFace.setImageBitmap(rec.crop)
        etName.hint = "Input name"
        builder.setPositiveButton("OK", DialogInterface.OnClickListener { dlg, i ->
            val name = etName.text.toString()
            if (name.isEmpty()) {
                return@OnClickListener
            }
            detector?.register(name, rec)
            //knownFaces.put(name, rec);
            dlg.dismiss()
        })
        builder.setView(dialogLayout)
        builder.show()
    }

    private fun updateResults(
        currTimestamp: Long,
        mappedRecognitions: List<SimilarityClassifier.Recognition>
    ) {
        tracker?.trackResults(mappedRecognitions, currTimestamp)
        trackingOverlay?.postInvalidate()
        computingDetection = false
        //adding = false;
        if (mappedRecognitions.isNotEmpty()) {
            LOGGER.i("Adding results")
            val rec: SimilarityClassifier.Recognition = mappedRecognitions[0]
            if (rec.getExtra() != null) {
                showAddFaceDialog(rec)
            }
        }
        runOnUiThread {
            showFrameInfo(previewWidth.toString() + "x" + previewHeight)
            showCropInfo(croppedBitmap!!.width.toString() + "x" + croppedBitmap!!.height)
            showInference(lastProcessingTimeMs.toString() + "ms")
        }
    }

    private fun onFacesDetected(currTimestamp: Long, faces: List<Face>, add: Boolean) {
        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap!!)
        val canvas = Canvas(cropCopyBitmap!!)
        val paint = Paint()
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f
        var minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API
        minimumConfidence = when (MODE) {
            DetectorMode.TF_OD_API -> MINIMUM_CONFIDENCE_TF_OD_API
        }
        val mappedRecognitions: MutableList<SimilarityClassifier.Recognition> =
            LinkedList<SimilarityClassifier.Recognition>()

        //final List<Classifier.Recognition> results = new ArrayList<>();
        // Note this can be done only once
        val sourceW = rgbFrameBitmap!!.width
        val sourceH = rgbFrameBitmap!!.height
        val targetW = portraitBmp!!.width
        val targetH = portraitBmp!!.height
        val transform = createTransform(sourceW, sourceH, targetW, targetH, sensorOrientation!!)
        val cv = Canvas(portraitBmp!!)

        // draws the original image in portrait mode.
        cv.drawBitmap(rgbFrameBitmap!!, transform, null)
        val cvFace = Canvas(faceBmp!!)
        val saved = false
        for (face in faces) {
            LOGGER.i("FACE $face")
            LOGGER.i("Running detection on face $currTimestamp")
            // val results = detector?.recognizeImage(croppedBitmap!!, true)!!
            val boundingBox = RectF(face.boundingBox)
            // final boolean goodConfidence = result.getConfidence() >= minimumConfidence;
            // val goodConfidence = true //face.get;
            val goodConfidence = true
            if (goodConfidence) {
                // maps crop coordinates to original
                cropToFrameTransform!!.mapRect(boundingBox)

                // maps original coordinates to portrait coordinates
                val faceBB = RectF(boundingBox)
                transform.mapRect(faceBB)

                // translates portrait to origin and scales to fit input inference size
                //cv.drawRect(faceBB, paint);
                val sx = TF_OD_API_INPUT_SIZE.toFloat() / faceBB.width()
                val sy = TF_OD_API_INPUT_SIZE.toFloat() / faceBB.height()
                val matrix = Matrix()
                matrix.postTranslate(-faceBB.left, -faceBB.top)
                matrix.postScale(sx, sy)
                cvFace.drawBitmap(portraitBmp!!, matrix, null)

                //canvas.drawRect(faceBB, paint);
                var label = ""
                var confidence = -1f
                var color = Color.BLUE
                var extra: Any? = null
                var crop: Bitmap? = null
                if (add) {
                    crop = Bitmap.createBitmap(
                        portraitBmp!!, faceBB.left.toInt(), faceBB.top.toInt(),
                        faceBB.width().toInt(), faceBB.height().toInt()
                    )
                }
                val startTime = SystemClock.uptimeMillis()
                val resultsAux: List<SimilarityClassifier.Recognition> =
                    detector?.recognizeImage(faceBmp, add)!!
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
                if (resultsAux.isNotEmpty()) {
                    val result: SimilarityClassifier.Recognition = resultsAux[0]
                    extra = result.getExtra()
                    val conf: Float = result.distance!!
                    /* Changed the condition to 2.0f from 1.0f */
                    if (conf < 1.0f) {
                        confidence = conf
                        label = result.getTitle()!!
                        color = if (result.getId().equals("0")) {
                            Color.GREEN
                        } else {
                            Color.RED
                        }
                    }
                }
                if (getCameraFacing() == CameraCharacteristics.LENS_FACING_FRONT) {
                    // camera is frontal so the image is flipped horizontally
                    // flips horizontally
                    val flip = Matrix()
                    if (sensorOrientation == 90 || sensorOrientation == 270) {
                        flip.postScale(1f, -1f, previewWidth / 2.0f, previewHeight / 2.0f)
                    } else {
                        flip.postScale(-1f, 1f, previewWidth / 2.0f, previewHeight / 2.0f)
                    }
                    //flip.postScale(1, -1, targetW / 2.0f, targetH / 2.0f);
                    flip.mapRect(boundingBox)
                }
                val result = SimilarityClassifier.Recognition(
                    "0", label, confidence, boundingBox
                )
                result.color = color
                result.setLocation(boundingBox)
                result.setExtra(extra)
                result.crop = crop
                mappedRecognitions.add(result)
            }
        }
        updateResults(currTimestamp, mappedRecognitions)
    }
}
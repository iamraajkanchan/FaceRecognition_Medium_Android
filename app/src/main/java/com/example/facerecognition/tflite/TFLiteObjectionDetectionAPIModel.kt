package com.example.facerecognition.tflite

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Trace
import com.example.facerecognition.env.Logger
import com.example.facerecognition.tflite.SimilarityClassifier.Recognition
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.math.sqrt


class TFLiteObjectDetectionAPIModel : SimilarityClassifier {
    //private val OUTPUT_SIZE = 512;
    private val OUTPUT_SIZE = 192

    // Float model
    private val IMAGE_MEAN = 128.0f
    private val IMAGE_STD = 128.0f

    private var isModelQuantized = false

    // Config values.
    private var inputSize = 0

    // Pre-allocated buffers.
    private val labels: Vector<String> = Vector<String>()
    private var intValues: IntArray? = null

    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private var outputLocations: Array<Array<FloatArray>>? = null

    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private var outputClasses: Array<FloatArray>? = null

    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private var outputScores: Array<FloatArray>? = null

    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private var numDetections: FloatArray? = null

    private var embeddings: Array<FloatArray>? = null

    private var imgData: ByteBuffer? = null

    private var tfLite: Interpreter? = null

    // Face Mask Detector Output
    private val output: Array<FloatArray>? = null

    private val registered: HashMap<String, Recognition> = HashMap()

    override fun register(name: String?, recognition: Recognition) {
        if (name != null) {
            registered[name] = recognition
        }
    }

    override fun recognizeImage(
        bitmap: Bitmap?,
        getExtra: Boolean
    ): List<Recognition> {
        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage")
        Trace.beginSection("preprocessBitmap")
        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        bitmap?.getPixels(
            intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height
        )
        imgData!!.rewind()
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues!![i * inputSize + j]
                if (isModelQuantized) {
                    // Quantized model
                    imgData!!.put((pixelValue shr 16 and 0xFF).toByte())
                    imgData!!.put((pixelValue shr 8 and 0xFF).toByte())
                    imgData!!.put((pixelValue and 0xFF).toByte())
                } else {
                    // Float model
                    imgData!!.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData!!.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData!!.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }
        Trace.endSection() // preprocessBitmap
        // Copy the input data into TensorFlow.
        Trace.beginSection("feed")
        val inputArray = arrayOf<Any>(imgData!!)
        Trace.endSection()
        // Here outputMap is changed to fit the Face Mask detector
        val outputMap: MutableMap<Int, Any> = HashMap()
        embeddings = Array(1) { FloatArray(OUTPUT_SIZE) }
        outputMap[0] = embeddings!!
        // Run the inference call.
        Trace.beginSection("run")
        //tfLite.runForMultipleInputsOutputs(inputArray, outputMapBack);
        tfLite!!.runForMultipleInputsOutputs(inputArray, outputMap)
        Trace.endSection()

        //    String res = "[";
        //    for (int i = 0; i < embeedings[0].length; i++) {
        //      res += embeedings[0][i];
        //      if (i < embeedings[0].length - 1) res += ", ";
        //    }
        //    res += "]";
        //    String res = "[";
        //    for (int i = 0; i < embeedings[0].length; i++) {
        //      res += embeedings[0][i];
        //      if (i < embeedings[0].length - 1) res += ", ";
        //    }
        //    res += "]";
        var distance = Float.MAX_VALUE
        val id = "0"
        var label: String? = "?"
        if (registered.size > 0) {
            //LOGGER.i("dataset SIZE: " + registered.size());
            val nearest = findNearest(embeddings!![0])
            if (nearest != null) {
                label = nearest.first
                distance = nearest.second!!
                LOGGER.i("nearest: $label - distance: $distance")
            }
        }
        val numDetectionsOutput = 1
        val recognitions: ArrayList<Recognition> = ArrayList(numDetectionsOutput)
        val rec = Recognition(id, label, distance, RectF())
        recognitions.add(rec)
        if (getExtra) {
            rec.setExtra(embeddings)
        }
        Trace.endSection()
        return recognitions
    }

    override fun enableStatLogging(debug: Boolean) {
    }

    override fun getStatString(): String = ""

    override fun close() {
    }

    override fun setNumThreads(num_threads: Int) {
        if (tfLite != null) {
            // tfLite?.setNumThreads(num_threads)
        }
    }

    override fun setUseNNAPI(isChecked: Boolean) {
        if (tfLite != null) {
            // tfLite?.setUseNNAPI(isChecked)
        }
    }

    // looks for the nearest embedding in the dataset (using L2 norm)
    // and returns the pair <id, distance>
    private fun findNearest(emb: FloatArray): Pair<String?, Float?>? {
        var ret: Pair<String?, Float>? = null
        for (entry: Map.Entry<String, Recognition> in registered.entries) {
            val name = entry.key
            val extras = entry.value.getExtra() as Array<*>
            if (extras.isNotEmpty()) {
                val knownEmb = extras[0] as FloatArray
                var distance = 0f
                for (i in emb.indices) {
                    val diff = emb[i] - knownEmb[i]
                    distance += diff * diff
                }
                distance = sqrt(distance.toDouble()).toFloat()
                if (ret == null || distance < ret.second) {
                    ret = Pair(name, distance)
                }
            }
        }
        return ret
    }

    companion object {
        // Only return this many results.
        private const val NUM_DETECTIONS = 1

        // Number of threads in the java app
        private const val NUM_THREADS = 4

        val LOGGER: Logger = Logger()

        /** Memory-map the model file in Assets.  */
        @Throws(IOException::class)
        private fun loadModelFile(assets: AssetManager, modelFilename: String): MappedByteBuffer {
            val fileDescriptor = assets.openFd(modelFilename)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel: FileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }

        /**
         * Initializes a native TensorFlow session for classifying images.
         *
         * @param assetManager The asset manager to be used to load assets.
         * @param modelFilename The filepath of the model GraphDef protocol buffer.
         * @param labelFilename The filepath of label file for classes.
         * @param inputSize The size of image input
         * @param isQuantized Boolean representing model is quantized or not
         */
        @Throws(IOException::class)
        fun create(
            assetManager: AssetManager,
            modelFilename: String,
            labelFilename: String,
            inputSize: Int,
            isQuantized: Boolean
        ): SimilarityClassifier {
            var d = TFLiteObjectDetectionAPIModel()
            val actualFilename = labelFilename.split("file:///android_asset/").toTypedArray()[1]
            val labelsInput: InputStream = assetManager.open(actualFilename)
            val br = BufferedReader(InputStreamReader(labelsInput))
            var line: String?
            while (br.readLine().also { line = it } != null) {
                LOGGER.w(line!!)
                d.labels.add(line)
            }
            br.close()
            d.inputSize = inputSize
            try {
                d.tfLite = Interpreter(loadModelFile(assetManager, modelFilename))
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
            d.isModelQuantized = isQuantized
            // Pre-allocate buffers.
            val numBytesPerChannel: Int = if (isQuantized) {
                1 // Quantized
            } else {
                4 // Floating point
            }
            d.imgData =
                ByteBuffer.allocateDirect(1 * d.inputSize * d.inputSize * 3 * numBytesPerChannel)
                    .order(ByteOrder.nativeOrder())
            d.intValues = IntArray(d.inputSize * d.inputSize)
            // d.tfLite.setNumThreads(NUM_THREADS)
            d.outputLocations = Array(1) { Array(NUM_DETECTIONS) { FloatArray(4) } }
            d.outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
            d.outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
            d.numDetections = FloatArray(1)
            return d
        }
    }

}
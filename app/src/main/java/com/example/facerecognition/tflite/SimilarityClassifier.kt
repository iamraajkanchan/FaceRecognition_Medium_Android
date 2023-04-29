package com.example.facerecognition.tflite

import android.graphics.Bitmap
import android.graphics.RectF

interface SimilarityClassifier {

    fun register(name: String?, recognition: Recognition)

    fun recognizeImage(bitmap: Bitmap?, getExtra: Boolean): List<Recognition>

    fun enableStatLogging(debug: Boolean)

    fun getStatString(): String?

    fun close()

    fun setNumThreads(num_threads: Int)

    fun setUseNNAPI(isChecked: Boolean)

    /** An immutable result returned by a Classifier describing what was recognized.  */
    /**
     * A unique identifier for what has been recognized. Specific to the class, not the instance of
     * the object.
     */
    /** Display name for the recognition.  */
    class Recognition(
        private val id: String?, private var title: String?, distance: Float?, location: RectF?
    ) {
        /**
         * A sortable score for how good the recognition is relative to others. Lower should be better.
         */
        val distance: Float?
        private var extra: Any?
        /** Optional location within the source image for the location of the recognized object.  */
        private var location: RectF?
        var color: Int?
        var crop: Bitmap?

        fun setExtra(extra: Any?) {
            this.extra = extra
        }

        fun getExtra() : Any? {
            return extra
        }

        fun getLocation(): RectF {
            return RectF(location)
        }

        fun setLocation(location: RectF?) {
            this.location = location
        }

        fun getTitle() : String? {
            return title
        }

        fun getId() : String? {
            return id
        }

        override fun toString(): String {
            var resultString = ""
            if (id != null) {
                resultString += "[$id] "
            }
            if (title != null) {
                resultString += "$title "
            }
            if (distance != null) {
                resultString += String.format("(%.1f%%) ", distance * 100.0f)
            }
            if (location != null) {
                resultString += location.toString() + " "
            }
            return resultString.trim { it <= ' ' }
        }

        init {
            this.distance = distance
            this.location = location
            color = null
            extra = null
            crop = null
        }
    }
}
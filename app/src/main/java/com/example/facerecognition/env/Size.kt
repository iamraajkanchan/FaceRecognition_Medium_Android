package com.example.facerecognition.env

import android.graphics.Bitmap
import android.text.TextUtils
import java.io.Serializable


class Size : Comparable<Size>, Serializable {
    // 1.4 went out with this UID so we'll need to maintain it to preserve pending queries when
    // upgrading.
    val serialVersionUID = 7689808733290872361L

    var width = 0
    var height = 0

    constructor(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    constructor(bmp: Bitmap) {
        width = bmp.width
        height = bmp.height
    }

    /**
     * Rotate a size by the given number of degrees.
     *
     * @param size Size to rotate.
     * @param rotation Degrees {0, 90, 180, 270} to rotate the size.
     * @return Rotated size.
     */
    fun getRotatedSize(size: Size, rotation: Int): Size? {
        return if (rotation % 180 != 0) {
            // The phone is portrait, therefore the camera is sideways and frame should be rotated.
           Size(size.height, size.width)
        } else size
    }

    fun parseFromString(sizeString: String): Size? {
        var sizeString = sizeString
        if (TextUtils.isEmpty(sizeString)) {
            return null
        }
        sizeString = sizeString.trim { it <= ' ' }

        // The expected format is "<width>x<height>".
        val components = sizeString.split("x").toTypedArray()
        return if (components.size == 2) {
            try {
                val width = components[0].toInt()
                val height = components[1].toInt()
                Size(width, height)
            } catch (e: NumberFormatException) {
                null
            }
        } else {
            null
        }
    }

    fun sizeStringToList(sizes: String?): List<Size>? {
        val sizeList: MutableList<Size> = ArrayList()
        if (sizes != null) {
            val pairs = sizes.split(",").toTypedArray()
            for (pair in pairs) {
                val size: Size = parseFromString(pair)!!
                if (size != null) {
                    sizeList.add(size)
                }
            }
        }
        return sizeList
    }

    fun sizeListToString(sizes: List<Size>?): String? {
        var sizesString: String? = ""
        if (sizes != null && sizes.size > 0) {
            sizesString = sizes[0].toString()
            for (i in 1 until sizes.size) {
                sizesString += "," + sizes[i].toString()
            }
        }
        return sizesString
    }

    fun dimensionsAsString(width: Int, height: Int): String {
        return width.toString() + "x" + height
    }

    fun aspectRatio(): Float {
        return width.toFloat() / height.toFloat()
    }

    override operator fun compareTo(other: Size): Int {
        return width * height - other.width * other.height
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other !is Size) {
            return false
        }
        return width == other.width && height == other.height
    }

    override fun hashCode(): Int {
        return width * 32713 + height
    }

    override fun toString(): String {
        return dimensionsAsString(width, height)
    }
}
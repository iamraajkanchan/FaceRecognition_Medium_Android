package com.example.facerecognition

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.os.Bundle
import android.os.HandlerThread
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.facerecognition.customview.AutoFitTextureView
import com.example.facerecognition.env.ImageUtils
import com.example.facerecognition.env.Logger
import java.io.IOException

class LegacyCameraConnectionFragment : Fragment {

    private val LOGGER: Logger = Logger()
    /** Conversion from screen rotation to JPEG orientation.  */
    private val KEY_FACING = "camera_facing"

    companion object {
        private val ORIENTATIONS = SparseIntArray().apply {
            append(Surface.ROTATION_0, 90)
            append(Surface.ROTATION_90, 0)
            append(Surface.ROTATION_180, 270)
            append(Surface.ROTATION_270, 180)
        }
    }

    private var camera: Camera? = Camera.open(getCameraId())
    private var imageListener: PreviewCallback? = null
    private var desiredSize: Size? = null
    private var facing = 0

    /** The layout identifier to inflate for this Fragment.  */
    private var layout = 0

    /** An [AutoFitTextureView] for camera preview.  */
    private var textureView: AutoFitTextureView? = null

    /**
     * [SurfaceTextureListener] handles several lifecycle events on a [ ].
     */
    private val surfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
            /* val index = getCameraId()
             val camera = Camera.open(index)*/
            try {
                val parameters = camera?.getParameters()
                val focusModes = parameters?.supportedFocusModes
                if (focusModes != null
                    && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
                ) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                }
                val cameraSizes = parameters?.supportedPreviewSizes
                val sizes = cameraSizes?.size?.let { arrayOfNulls<Size>(it) }
                var i = 0
                if (cameraSizes != null) {
                    for (size in cameraSizes) {
                        sizes?.set(i++, Size(size.width, size.height))
                    }
                }
                val previewSize: Size? = CameraConnectionFragment.chooseOptimalSize(
                    sizes!!, desiredSize!!.width, desiredSize!!.height
                )
                parameters.setPreviewSize(previewSize?.width!!, previewSize.height)
                camera?.setDisplayOrientation(90)
                camera?.setParameters(parameters)
                camera?.setPreviewTexture(texture)
            } catch (exception: IOException) {
                camera?.release()
            }
            camera?.setPreviewCallbackWithBuffer(imageListener)
            val s = camera?.getParameters()?.previewSize
            camera?.addCallbackBuffer(ByteArray(ImageUtils.getYUVByteSize(s!!.height, s.width)))
            textureView?.setAspectRatio(s!!.height, s.width)
            camera?.startPreview()
        }

        override fun onSurfaceTextureSizeChanged(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /** An additional thread for running tasks that shouldn't block the UI.  */
    private var backgroundThread: HandlerThread? = null

    constructor(
        imageListener: PreviewCallback?, layout: Int, desiredSize: Size?, facing: Int
    ) {
        this.imageListener = imageListener
        this.layout = layout
        this.desiredSize = desiredSize
        this.facing = facing
    }

    override fun setArguments(args: Bundle?) {
        super.setArguments(args)
        facing = args?.getInt(KEY_FACING, Camera.CameraInfo.CAMERA_FACING_FRONT)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById<View>(R.id.texture) as AutoFitTextureView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView?.isAvailable == true) {
            camera!!.startPreview()
        } else {
            textureView?.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        stopCamera()
        stopBackgroundThread()
        super.onPause()
    }

    /** Starts a background thread and its [HandlerThread].  */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread!!.start()
    }

    /** Stops the background thread and its [HandlerThread].  */
    private fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
        } catch (e: InterruptedException) {
            LOGGER.e(e, "Exception!")
        }
    }

    protected fun stopCamera() {
        if (camera != null) {
            camera!!.stopPreview()
            camera!!.setPreviewCallback(null)
            camera!!.release()
            camera = null
        }
    }

    private fun getCameraId(): Int {
        val ci = Camera.CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, ci)
            if (ci.facing == facing) return i
        }
        return -1 // No camera found
    }
}
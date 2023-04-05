package jp.yuppe.camera2step2

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    val TAG = MainActivity::class.simpleName
    val REQUEST_CODE_PERMISSIONS = 1001
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private lateinit var textureViewFront: TextureView
    private lateinit var textureViewBack: TextureView
    private lateinit var cameraIdFront: String
    private lateinit var cameraIdBack: String
    private lateinit var backgroundHandlerThreadFront: HandlerThread
    private lateinit var backgroundHandlerFront: Handler
    private lateinit var backgroundHandlerThreadBack: HandlerThread
    private lateinit var backgroundHandlerBack: Handler
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDeviceFront: CameraDevice
    private lateinit var cameraDeviceBack: CameraDevice
    private lateinit var captureRequestBuilderFront: CaptureRequest.Builder
    private lateinit var captureRequestBuilderBack: CaptureRequest.Builder
    private lateinit var cameraCaptureSessionFront: CameraCaptureSession
    private lateinit var cameraCaptureSessionBack: CameraCaptureSession
    private lateinit var previewSizeFront: Size
    private lateinit var previewSizeBack: Size
    private lateinit var videoSizeFront: Size
    private lateinit var videoSizeBack: Size
    private var shouldProceedWithOnResume: Boolean = true
    private var orientations: SparseIntArray = SparseIntArray(4).apply {
        append(Surface.ROTATION_0, 0)
        append(Surface.ROTATION_90, 90)
        append(Surface.ROTATION_180, 180)
        append(Surface.ROTATION_270, 270)
    }
    private lateinit var mediaRecorderFront: MediaRecorder
    private lateinit var mediaRecorderBack: MediaRecorder
    private var isRecordingFront: Boolean = false
    private var isRecordingBack: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // パーミッションをリクエストする
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        textureViewFront = findViewById(R.id.texture_view_front)
        textureViewBack = findViewById(R.id.texture_view_back)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager


        findViewById<Button>(R.id.recordButton).apply {
            setOnClickListener {
                if (isRecordingFront) {
                    mediaRecorderFront.stop()
                    mediaRecorderFront.reset()
                } else {
                    mediaRecorderFront = MediaRecorder()
                    setupMediaRecorderFront()
                    startRecordingFront()
                }
                isRecordingFront = !isRecordingFront
            }
        }
        findViewById<Button>(R.id.recordButtonBack).apply {
            setOnClickListener {
                if (isRecordingBack) {
                    mediaRecorderBack.stop()
                    mediaRecorderBack.reset()
                } else {
                    mediaRecorderBack = MediaRecorder()
                    setupMediaRecorderBack()
                    startRecordingBack()
                }
                isRecordingBack = !isRecordingBack            }
        }

        //startBackgroundThread()
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureViewFront.isAvailable && textureViewBack.isAvailable && shouldProceedWithOnResume) {
            setupCameraFront()
            setupCameraBack()
        } else if (!textureViewFront.isAvailable || !textureViewBack.isAvailable){
            textureViewFront.surfaceTextureListener = surfaceTextureListenerFront
            textureViewBack.surfaceTextureListener = surfaceTextureListenerBack
        }
        shouldProceedWithOnResume = !shouldProceedWithOnResume
    }

    private fun setupCameraFront() {
        val cameraIds: Array<String> = cameraManager.cameraIdList

        for (id in cameraIds) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(id)
            val facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)

            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                val streamConfigurationMap: StreamConfigurationMap? = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                if (streamConfigurationMap != null) {
                    previewSizeFront = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!
                    videoSizeFront = streamConfigurationMap.getOutputSizes(MediaRecorder::class.java).maxByOrNull { it.height * it.width }!!
                    cameraIdFront = id
                }
            }
        }
    }
    private fun setupCameraBack() {
        val cameraIds: Array<String> = cameraManager.cameraIdList

        for (id in cameraIds) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(id)
            val facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)

            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                val streamConfigurationMap: StreamConfigurationMap? = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                if (streamConfigurationMap != null) {
                    previewSizeBack = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!
                    videoSizeBack = streamConfigurationMap.getOutputSizes(MediaRecorder::class.java).maxByOrNull { it.height * it.width }!!
                    cameraIdBack = id
                }
            }
        }
    }

    private fun wasCameraPermissionWasGiven(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun connectCameraFront() {
        cameraManager.openCamera(cameraIdFront, cameraStateCallbackFront, null) // backgroundHandlerFront)
    }
    @SuppressLint("MissingPermission")
    private fun connectCameraBack() {
        cameraManager.openCamera(cameraIdBack, cameraStateCallbackBack, null) //backgroundHandlerBack)
    }

    private fun setupMediaRecorderFront() {
        mediaRecorderFront.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorderFront.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorderFront.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorderFront.setVideoSize(videoSizeFront.width, videoSizeFront.height)
        mediaRecorderFront.setVideoFrameRate(30)
        mediaRecorderFront.setOutputFile(createFileFront().absolutePath)
        mediaRecorderFront.setVideoEncodingBitRate(10_000_000)
        mediaRecorderFront.prepare()
    }
    private fun setupMediaRecorderBack() {
        mediaRecorderBack.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorderBack.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorderBack.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorderBack.setVideoSize(videoSizeBack.width, videoSizeBack.height)
        mediaRecorderBack.setVideoFrameRate(30)
        mediaRecorderBack.setOutputFile(createFileBack().absolutePath)
        mediaRecorderBack.setVideoEncodingBitRate(10_000_000)
        mediaRecorderBack.prepare()
    }

    private fun startRecordingFront() {
        val surfaceTexture : SurfaceTexture? = textureViewFront.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(previewSizeFront.width, previewSizeFront.height)
        val previewSurface: Surface = Surface(surfaceTexture)
        val recordingSurface = mediaRecorderFront.surface
        captureRequestBuilderFront = cameraDeviceFront.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        captureRequestBuilderFront.addTarget(previewSurface)
        captureRequestBuilderFront.addTarget(recordingSurface)

        cameraDeviceFront.createCaptureSession(listOf(previewSurface, recordingSurface), captureStateVideoCallbackFront, null) // backgroundHandlerFront)
    }
    private fun startRecordingBack() {
        val surfaceTexture : SurfaceTexture? = textureViewBack.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(previewSizeBack.width, previewSizeBack.height)
        val previewSurface: Surface = Surface(surfaceTexture)
        val recordingSurface = mediaRecorderBack.surface
        captureRequestBuilderBack = cameraDeviceBack.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        captureRequestBuilderBack.addTarget(previewSurface)
        captureRequestBuilderBack.addTarget(recordingSurface)

        cameraDeviceBack.createCaptureSession(listOf(previewSurface, recordingSurface), captureStateVideoCallbackBack, null) // backgroundHandlerBack)
    }

    /**
     * Surface Texture Listener
     */

    private val surfaceTextureListenerFront = object : TextureView.SurfaceTextureListener {
        @SuppressLint("MissingPermission")
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            if (wasCameraPermissionWasGiven()) {
                setupCameraFront()
                connectCameraFront()
            }
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
        }
    }
    private val surfaceTextureListenerBack = object : TextureView.SurfaceTextureListener {
        @SuppressLint("MissingPermission")
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            if (wasCameraPermissionWasGiven()) {
                setupCameraBack()
                connectCameraBack()
            }
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
        }
    }

    /**
     * Camera State Callbacks
     */

    private val cameraStateCallbackFront = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDeviceFront = camera
            val surfaceTexture : SurfaceTexture? = textureViewFront.surfaceTexture
            surfaceTexture?.setDefaultBufferSize(previewSizeFront.width, previewSizeFront.height)
            val previewSurface: Surface = Surface(surfaceTexture)

            captureRequestBuilderFront = cameraDeviceFront.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilderFront.addTarget(previewSurface)

            cameraDeviceFront.createCaptureSession(listOf(previewSurface/*, imageReader.surface*/), captureStateCallbackFront, null)
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            val errorMsg = when(error) {
                ERROR_CAMERA_DEVICE -> "Fatal (device)"
                ERROR_CAMERA_DISABLED -> "Device policy"
                ERROR_CAMERA_IN_USE -> "Camera in use"
                ERROR_CAMERA_SERVICE -> "Fatal (service)"
                ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                else -> "Unknown"
            }
            Log.e(TAG, "Error when trying to connect camera $errorMsg")
        }
    }
    private val cameraStateCallbackBack = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDeviceBack = camera
            val surfaceTexture : SurfaceTexture? = textureViewBack.surfaceTexture
            surfaceTexture?.setDefaultBufferSize(previewSizeBack.width, previewSizeBack.height)
            val previewSurface: Surface = Surface(surfaceTexture)

            captureRequestBuilderBack = cameraDeviceBack.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilderBack.addTarget(previewSurface)

            cameraDeviceBack.createCaptureSession(listOf(previewSurface/*, imageReader.surface*/), captureStateCallbackBack, null)
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            val errorMsg = when(error) {
                ERROR_CAMERA_DEVICE -> "Fatal (device)"
                ERROR_CAMERA_DISABLED -> "Device policy"
                ERROR_CAMERA_IN_USE -> "Camera in use"
                ERROR_CAMERA_SERVICE -> "Fatal (service)"
                ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                else -> "Unknown"
            }
            Log.e(TAG, "Error when trying to connect camera $errorMsg")
        }
    }

    /**
     * Background Thread
     */
    private fun startBackgroundThread() {
        backgroundHandlerThreadFront = HandlerThread("CameraVideoThreadFront")
        backgroundHandlerThreadFront.start()
        backgroundHandlerFront = Handler(backgroundHandlerThreadFront.looper)

        backgroundHandlerThreadBack = HandlerThread("CameraVideoThreadBack")
        backgroundHandlerThreadBack.start()
        backgroundHandlerBack = Handler(backgroundHandlerThreadBack.looper)
    }

    private fun stopBackgroundThread() {
        backgroundHandlerThreadFront.quitSafely()
        backgroundHandlerThreadFront.join()

        backgroundHandlerThreadBack.quitSafely()
        backgroundHandlerThreadBack.join()
    }

    /**
     * Capture State Callback
     */

    private val captureStateCallbackFront = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {

        }
        override fun onConfigured(session: CameraCaptureSession) {
            cameraCaptureSessionFront = session

            cameraCaptureSessionFront.setRepeatingRequest(
                captureRequestBuilderFront.build(),
                null,
                backgroundHandlerFront
            )
        }
    }
    private val captureStateCallbackBack = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {

        }
        override fun onConfigured(session: CameraCaptureSession) {
            cameraCaptureSessionBack = session

            cameraCaptureSessionBack.setRepeatingRequest(
                captureRequestBuilderBack.build(),
                null,
                backgroundHandlerBack
            )
        }
    }

    private val captureStateVideoCallbackFront = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "Configuration failed")
        }
        override fun onConfigured(session: CameraCaptureSession) {
            cameraCaptureSessionFront = session
            captureRequestBuilderFront.set(CaptureRequest.CONTROL_AF_MODE, CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            try {
                cameraCaptureSessionFront.setRepeatingRequest(
                    captureRequestBuilderFront.build(), null,
                    backgroundHandlerFront
                )
                mediaRecorderFront.start()
            } catch (e: CameraAccessException) {
                e.printStackTrace()
                Log.e(TAG, "Failed to start camera preview because it couldn't access the camera")
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }

        }
    }
    private val captureStateVideoCallbackBack = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "Configuration failed")
        }
        override fun onConfigured(session: CameraCaptureSession) {
            cameraCaptureSessionBack = session
            captureRequestBuilderBack.set(CaptureRequest.CONTROL_AF_MODE, CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            try {
                cameraCaptureSessionBack.setRepeatingRequest(
                    captureRequestBuilderBack.build(), null,
                    backgroundHandlerBack
                )
                mediaRecorderBack.start()
            } catch (e: CameraAccessException) {
                e.printStackTrace()
                Log.e(TAG, "Failed to start camera preview because it couldn't access the camera")
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }

        }
    }

    /**
     * Capture Callback
     */
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {}

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) { }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {

        }
    }

    /**
     * ImageAvailable Listener
     */
    val onImageAvailableListener = object: ImageReader.OnImageAvailableListener{
        override fun onImageAvailable(reader: ImageReader) {
            Toast.makeText(this@MainActivity, "Photo Taken!", Toast.LENGTH_SHORT).show()
            val image: Image = reader.acquireLatestImage()
            image.close()
        }
    }

    /**
     * File Creation
     */

    private fun createFileFront(): File {
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        val file = File(filesDir, "VID_${sdf.format(Date())}_FRONT.mp4")

        // /data/user/0/com.tomerpacific.camera2api/files/VID_2023_04_04_16_25_15_765.mp4
        Log.d(TAG, "*** path: " + file.absolutePath)

        return file
    }
    private fun createFileBack(): File {
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        val file = File(filesDir, "VID_${sdf.format(Date())}_BACK.mp4")

        // /data/user/0/com.tomerpacific.camera2api/files/VID_2023_04_04_16_25_15_765.mp4
        Log.d(TAG, "*** path: " + file.absolutePath)

        return file
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // 許可された場合の処理
            } else {
                // 許可されなかった場合の処理
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                //finish()
            }
        }
    }
}
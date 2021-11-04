package org.reactnative.camera

import android.Manifest
import com.facebook.react.uimanager.ThemedReactContext
import com.google.android.cameraview.CameraView
import com.facebook.react.bridge.LifecycleEventListener
import org.reactnative.camera.tasks.BarCodeScannerAsyncTaskDelegate
import org.reactnative.camera.tasks.FaceDetectorAsyncTaskDelegate
import org.reactnative.camera.tasks.BarcodeDetectorAsyncTaskDelegate
import org.reactnative.camera.tasks.TextRecognizerAsyncTaskDelegate
import org.reactnative.camera.tasks.PictureSavedDelegate
import java.util.Queue
import com.facebook.react.bridge.Promise
import java.util.concurrent.ConcurrentLinkedQueue
import com.facebook.react.bridge.ReadableMap
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import android.view.ScaleGestureDetector
import kotlin.jvm.Volatile
import com.google.zxing.MultiFormatReader
import org.reactnative.facedetector.RNFaceDetector
import org.reactnative.barcodedetector.RNBarcodeDetector
import android.view.View
import android.graphics.Color
import android.annotation.SuppressLint
import java.lang.Runnable
import java.lang.Exception
import com.facebook.react.bridge.WritableMap
import org.reactnative.camera.RNCameraViewHelper
import org.reactnative.camera.utils.RNFileUtils
import android.media.CamcorderProfile
import java.io.IOException
import java.util.EnumMap
import com.google.zxing.DecodeHintType
import java.util.EnumSet
import org.reactnative.camera.CameraModule
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import android.graphics.Rect
import java.lang.RuntimeException
import android.view.MotionEvent
import com.facebook.react.bridge.WritableArray
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.ImageFormat
import android.util.DisplayMetrics
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.ScaleGestureDetector.OnScaleGestureListener
import org.reactnative.camera.tasks.ResolveTakenPictureAsyncTask
import android.os.AsyncTask
import android.util.Log
import android.view.GestureDetector
import com.facebook.react.bridge.Arguments
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import org.reactnative.camera.tasks.BarCodeScannerAsyncTask
import org.reactnative.camera.tasks.FaceDetectorAsyncTask
import org.reactnative.camera.tasks.BarcodeDetectorAsyncTask
import org.reactnative.camera.tasks.TextRecognizerAsyncTask
import kotlin.experimental.inv

class RNCameraView(private val mThemedReactContext: ThemedReactContext) : CameraView(
    mThemedReactContext, true
), LifecycleEventListener, BarCodeScannerAsyncTaskDelegate, FaceDetectorAsyncTaskDelegate,
    BarcodeDetectorAsyncTaskDelegate, TextRecognizerAsyncTaskDelegate, PictureSavedDelegate {
    private val mPictureTakenPromises: Queue<Promise> = ConcurrentLinkedQueue()
    private val mPictureTakenOptions: MutableMap<Promise, ReadableMap> = ConcurrentHashMap()
    private val mPictureTakenDirectories: MutableMap<Promise, File> = ConcurrentHashMap()
    private var mVideoRecordedPromise: Promise? = null
    private var mBarCodeTypes: List<String>? = null
    private var mDetectedImageInEvent = false
    private var mScaleGestureDetector: ScaleGestureDetector? = null
    private var mGestureDetector: GestureDetector? = null
    private var mIsPaused = false
    private var mIsNew = true
    private var invertImageData = false
    private var mIsRecording = false
    private var mIsRecordingInterrupted = false
    private var mUseNativeZoom = false

    // Concurrency lock for scanners to avoid flooding the runtime
    @Volatile
    var barCodeScannerTaskLock = false

    @Volatile
    var faceDetectorTaskLock = false

    @Volatile
    var googleBarcodeDetectorTaskLock = false

    @Volatile
    var textRecognizerTaskLock = false

    // timestamp
    private var startRecordingUnixTime: Long? = null
    private var endRecordingUnixTime: Long? = null
    private var torchOnUnixTime: Long? = null
    private var torchOffUnixTime: Long? = null

    // torch period props
    private var torchOnAt = 0.0f
    private var torchOffAt = 0.0f
    private var scanDuration = 0.0f

    // Scanning-related properties
    private var mMultiFormatReader: MultiFormatReader? = null
    private var mFaceDetector: RNFaceDetector? = null
    private var mGoogleBarcodeDetector: RNBarcodeDetector? = null
    private var mShouldDetectFaces = false
    private var mShouldGoogleDetectBarcodes = false
    private var mShouldScanBarCodes = false
    private var mShouldRecognizeText = false
    private var mShouldDetectTouches = false
    private var mFaceDetectorMode = RNFaceDetector.FAST_MODE
    private var mFaceDetectionLandmarks = RNFaceDetector.NO_LANDMARKS
    private var mFaceDetectionClassifications = RNFaceDetector.NO_CLASSIFICATIONS
    private var mGoogleVisionBarCodeType = RNBarcodeDetector.ALL_FORMATS
    private var mGoogleVisionBarCodeMode = RNBarcodeDetector.NORMAL_MODE
    private var mTrackingEnabled = true
    private var mPaddingX = 0
    private var mPaddingY = 0

    // Limit Android Scan Area
    private var mLimitScanArea = false
    private var mScanAreaX = 0.0f
    private var mScanAreaY = 0.0f
    private var mScanAreaWidth = 0.0f
    private var mScanAreaHeight = 0.0f
    private var mCameraViewWidth = 0
    private var mCameraViewHeight = 0
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val preview = view ?: return
        val width = (right - left).toFloat()
        val height = (bottom - top).toFloat()
        val ratio = aspectRatio!!.toFloat()
        val orientation = resources.configuration.orientation
        val correctHeight: Int
        val correctWidth: Int
        setBackgroundColor(Color.BLACK)
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (ratio * height < width) {
                correctHeight = (width / ratio).toInt()
                correctWidth = width.toInt()
            } else {
                correctWidth = (height * ratio).toInt()
                correctHeight = height.toInt()
            }
        } else {
            if (ratio * width > height) {
                correctHeight = (width * ratio).toInt()
                correctWidth = width.toInt()
            } else {
                correctWidth = (height / ratio).toInt()
                correctHeight = height.toInt()
            }
        }
        val paddingX = ((width - correctWidth) / 2).toInt()
        val paddingY = ((height - correctHeight) / 2).toInt()
        mPaddingX = paddingX
        mPaddingY = paddingY
        preview.layout(paddingX, paddingY, correctWidth + paddingX, correctHeight + paddingY)
    }

    @SuppressLint("all")
    override fun requestLayout() {
        // React handles this for us, so we don't need to call super.requestLayout();
    }

    fun getCurrentUnixTime(): Long {
        return System.currentTimeMillis() / 1L;
    }

    fun setBarCodeTypes(barCodeTypes: List<String>?) {
        mBarCodeTypes = barCodeTypes
        initBarcodeReader()
    }

    fun setDetectedImageInEvent(detectedImageInEvent: Boolean) {
        mDetectedImageInEvent = detectedImageInEvent
    }

    fun takePicture(options: ReadableMap, promise: Promise, cacheDirectory: File) {
        mBgHandler.post {
            mPictureTakenPromises.add(promise)
            mPictureTakenOptions[promise] = options
            mPictureTakenDirectories[promise] = cacheDirectory
            try {
                super@RNCameraView.takePicture(options)
            } catch (e: Exception) {
                mPictureTakenPromises.remove(promise)
                mPictureTakenOptions.remove(promise)
                mPictureTakenDirectories.remove(promise)
                promise.reject("E_TAKE_PICTURE_FAILED", e.message)
            }
        }
    }

    override fun onPictureSaved(response: WritableMap) {
        RNCameraViewHelper.emitPictureSavedEvent(this, response)
    }

    fun record(options: ReadableMap, promise: Promise, cacheDirectory: File?) {
        mBgHandler.post {
            try {
                val path =
                    if (options.hasKey("path")) options.getString("path") else RNFileUtils.getOutputFilePath(
                        cacheDirectory,
                        ".mp4"
                    )
                val maxDuration =
                    if (options.hasKey("maxDuration")) options.getInt("maxDuration") else -1
                val maxFileSize =
                    if (options.hasKey("maxFileSize")) options.getInt("maxFileSize") else -1
                val fps = if (options.hasKey("fps")) options.getInt("fps") else -1
                var profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH)
                if (options.hasKey("quality")) {
                    profile = RNCameraViewHelper.getCamcorderProfile(options.getInt("quality"))
                }
                if (options.hasKey("videoBitrate")) {
                    profile.videoBitRate = options.getInt("videoBitrate")
                }
                var recordAudio = true
                if (options.hasKey("mute")) {
                    recordAudio = !options.getBoolean("mute")
                }
                var orientation = Constants.ORIENTATION_AUTO
                if (options.hasKey("orientation")) {
                    orientation = options.getInt("orientation")
                }

                if (options.hasKey("maxDuration")) {
                    scanDuration = options.getDouble("maxDuration").toFloat()
                }
                if (options.hasKey("torchOnAt")) {
                    torchOnAt = options.getDouble("torchOnAt").toFloat()
                }
                if (options.hasKey("torchOffAt")) {
                    torchOffAt = options.getDouble("torchOffAt").toFloat()
                }

                if (super@RNCameraView.record(
                        path,
                        maxDuration * 1000,
                        maxFileSize,
                        recordAudio,
                        profile,
                        orientation,
                        fps
                    )
                ) {
                    mIsRecording = true
                    mVideoRecordedPromise = promise
                } else {
                    promise.reject(
                        "E_RECORDING_FAILED",
                        "Starting video recording failed. Another recording might be in progress."
                    )
                }
            } catch (e: IOException) {
                promise.reject(
                    "E_RECORDING_FAILED",
                    "Starting video recording failed - could not create video file."
                )
            }
        }
    }

    /**
     * Initialize the barcode decoder.
     * Supports all iOS codes except [code138, code39mod43, itf14]
     * Additionally supports [codabar, code128, maxicode, rss14, rssexpanded, upc_a, upc_ean]
     */
    private fun initBarcodeReader() {
        mMultiFormatReader = MultiFormatReader()
        val hints = EnumMap<DecodeHintType, Any?>(
            DecodeHintType::class.java
        )
        val decodeFormats = EnumSet.noneOf(BarcodeFormat::class.java)
        if (mBarCodeTypes != null) {
            for (code in mBarCodeTypes!!) {
                val formatString = CameraModule.VALID_BARCODE_TYPES[code] as String?
                if (formatString != null) {
                    decodeFormats.add(BarcodeFormat.valueOf(formatString))
                }
            }
        }
        hints[DecodeHintType.POSSIBLE_FORMATS] = decodeFormats
        mMultiFormatReader!!.setHints(hints)
    }

    fun setShouldScanBarCodes(shouldScanBarCodes: Boolean) {
        if (shouldScanBarCodes && mMultiFormatReader == null) {
            initBarcodeReader()
        }
        mShouldScanBarCodes = shouldScanBarCodes
        scanning =
            mShouldDetectFaces || mShouldGoogleDetectBarcodes || mShouldScanBarCodes || mShouldRecognizeText
    }

    override fun onBarCodeRead(barCode: Result, width: Int, height: Int, imageData: ByteArray) {
        val barCodeType = barCode.barcodeFormat.toString()
        if (!mShouldScanBarCodes || !mBarCodeTypes!!.contains(barCodeType)) {
            return
        }
        val compressedImage: ByteArray? = if (mDetectedImageInEvent) {
            try {
                // https://stackoverflow.com/a/32793908/122441
                val yuvImage = YuvImage(imageData, ImageFormat.NV21, width, height, null)
                val imageStream = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, imageStream)
                imageStream.toByteArray()
            } catch (e: Exception) {
                throw RuntimeException(
                    String.format(
                        "Error decoding imageData from NV21 format (%d bytes)",
                        imageData.size
                    ), e
                )
            }
        } else {
            null
        }
        RNCameraViewHelper.emitBarCodeReadEvent(this, barCode, width, height, compressedImage)
    }

    override fun onBarCodeScanningTaskCompleted() {
        barCodeScannerTaskLock = false
        if (mMultiFormatReader != null) {
            mMultiFormatReader!!.reset()
        }
    }

    // Limit Scan Area
    fun setRectOfInterest(x: Float, y: Float, width: Float, height: Float) {
        mLimitScanArea = true
        mScanAreaX = x
        mScanAreaY = y
        mScanAreaWidth = width
        mScanAreaHeight = height
    }

    fun setCameraViewDimensions(width: Int, height: Int) {
        mCameraViewWidth = width
        mCameraViewHeight = height
    }

    fun setShouldDetectTouches(shouldDetectTouches: Boolean) {
        mGestureDetector = if (!mShouldDetectTouches && shouldDetectTouches) {
            GestureDetector(mThemedReactContext, onGestureListener)
        } else {
            null
        }
        mShouldDetectTouches = shouldDetectTouches
    }

    fun setUseNativeZoom(useNativeZoom: Boolean) {
        mScaleGestureDetector = if (!mUseNativeZoom && useNativeZoom) {
            ScaleGestureDetector(mThemedReactContext, onScaleGestureListener)
        } else {
            null
        }
        mUseNativeZoom = useNativeZoom
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mUseNativeZoom) {
            mScaleGestureDetector!!.onTouchEvent(event)
        }
        if (mShouldDetectTouches) {
            mGestureDetector!!.onTouchEvent(event)
        }
        return true
    }

    /**
     * Initial setup of the face detector
     */
    private fun setupFaceDetector() {
        mFaceDetector = RNFaceDetector(mThemedReactContext)
        mFaceDetector!!.setMode(mFaceDetectorMode)
        mFaceDetector!!.setLandmarkType(mFaceDetectionLandmarks)
        mFaceDetector!!.setClassificationType(mFaceDetectionClassifications)
        mFaceDetector!!.setTracking(mTrackingEnabled)
    }

    fun setFaceDetectionLandmarks(landmarks: Int) {
        mFaceDetectionLandmarks = landmarks
        if (mFaceDetector != null) {
            mFaceDetector!!.setLandmarkType(landmarks)
        }
    }

    fun setFaceDetectionClassifications(classifications: Int) {
        mFaceDetectionClassifications = classifications
        if (mFaceDetector != null) {
            mFaceDetector!!.setClassificationType(classifications)
        }
    }

    fun setFaceDetectionMode(mode: Int) {
        mFaceDetectorMode = mode
        if (mFaceDetector != null) {
            mFaceDetector!!.setMode(mode)
        }
    }

    fun setTracking(trackingEnabled: Boolean) {
        mTrackingEnabled = trackingEnabled
        if (mFaceDetector != null) {
            mFaceDetector!!.setTracking(trackingEnabled)
        }
    }

    fun setShouldDetectFaces(shouldDetectFaces: Boolean) {
        if (shouldDetectFaces && mFaceDetector == null) {
            setupFaceDetector()
        }
        mShouldDetectFaces = shouldDetectFaces
        scanning =
            mShouldDetectFaces || mShouldGoogleDetectBarcodes || mShouldScanBarCodes || mShouldRecognizeText
    }

    override fun onFacesDetected(data: WritableArray) {
        if (!mShouldDetectFaces) {
            return
        }
        RNCameraViewHelper.emitFacesDetectedEvent(this, data)
    }

    override fun onFaceDetectionError(faceDetector: RNFaceDetector) {
        if (!mShouldDetectFaces) {
            return
        }
        RNCameraViewHelper.emitFaceDetectionErrorEvent(this, faceDetector)
    }

    override fun onFaceDetectingTaskCompleted() {
        faceDetectorTaskLock = false
    }

    /**
     * Initial setup of the barcode detector
     */
    private fun setupBarcodeDetector() {
        mGoogleBarcodeDetector = RNBarcodeDetector(mThemedReactContext)
        mGoogleBarcodeDetector!!.setBarcodeType(mGoogleVisionBarCodeType)
    }

    fun setShouldGoogleDetectBarcodes(shouldDetectBarcodes: Boolean) {
        if (shouldDetectBarcodes && mGoogleBarcodeDetector == null) {
            setupBarcodeDetector()
        }
        mShouldGoogleDetectBarcodes = shouldDetectBarcodes
        scanning =
            mShouldDetectFaces || mShouldGoogleDetectBarcodes || mShouldScanBarCodes || mShouldRecognizeText
    }

    fun setGoogleVisionBarcodeType(barcodeType: Int) {
        mGoogleVisionBarCodeType = barcodeType
        if (mGoogleBarcodeDetector != null) {
            mGoogleBarcodeDetector!!.setBarcodeType(barcodeType)
        }
    }

    fun setGoogleVisionBarcodeMode(barcodeMode: Int) {
        mGoogleVisionBarCodeMode = barcodeMode
    }

    override fun onBarcodesDetected(
        barcodesDetected: WritableArray,
        width: Int,
        height: Int,
        imageData: ByteArray
    ) {
        if (!mShouldGoogleDetectBarcodes) {
            return
        }

        // See discussion in https://github.com/react-native-community/react-native-camera/issues/2786
        val compressedImage: ByteArray? = if (mDetectedImageInEvent) {
            try {
                // https://stackoverflow.com/a/32793908/122441
                val yuvImage = YuvImage(imageData, ImageFormat.NV21, width, height, null)
                val imageStream = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, imageStream)
                imageStream.toByteArray()
            } catch (e: Exception) {
                throw RuntimeException(
                    String.format(
                        "Error decoding imageData from NV21 format (%d bytes)",
                        imageData.size
                    ), e
                )
            }
        } else {
            null
        }
        RNCameraViewHelper.emitBarcodesDetectedEvent(this, barcodesDetected, compressedImage)
    }

    override fun onBarcodeDetectionError(barcodeDetector: RNBarcodeDetector) {
        if (!mShouldGoogleDetectBarcodes) {
            return
        }
        RNCameraViewHelper.emitBarcodeDetectionErrorEvent(this, barcodeDetector)
    }

    override fun onBarcodeDetectingTaskCompleted() {
        googleBarcodeDetectorTaskLock = false
    }

    /**
     *
     * Text recognition
     */
    fun setShouldRecognizeText(shouldRecognizeText: Boolean) {
        mShouldRecognizeText = shouldRecognizeText
        scanning =
            mShouldDetectFaces || mShouldGoogleDetectBarcodes || mShouldScanBarCodes || mShouldRecognizeText
    }

    override fun onTextRecognized(serializedData: WritableArray) {
        if (!mShouldRecognizeText) {
            return
        }
        RNCameraViewHelper.emitTextRecognizedEvent(this, serializedData)
    }

    override fun onTextRecognizerTaskCompleted() {
        textRecognizerTaskLock = false
    }

    /**
     *
     * End Text Recognition  */
    override fun onHostResume() {
        if (hasCameraPermissions()) {
            mBgHandler.post {
                if (mIsPaused && !isCameraOpened || mIsNew) {
                    mIsPaused = false
                    mIsNew = false
                    start()
                }
            }
        } else {
            RNCameraViewHelper.emitMountErrorEvent(
                this,
                "Camera permissions not granted - component could not be rendered."
            )
        }
    }

    override fun onHostPause() {
        if (mIsRecording) {
            mIsRecordingInterrupted = true
        }
        if (!mIsPaused && isCameraOpened) {
            mIsPaused = true
            stop()
        }
    }

    override fun onHostDestroy() {
        if (mFaceDetector != null) {
            mFaceDetector!!.release()
        }
        if (mGoogleBarcodeDetector != null) {
            mGoogleBarcodeDetector!!.release()
        }
        mMultiFormatReader = null
        mThemedReactContext.removeLifecycleEventListener(this)

        // camera release can be quite expensive. Run in on bg handler
        // and cleanup last once everything has finished
        mBgHandler.post {
            stop()
            cleanup()
        }
    }

    private fun onZoom(scale: Float) {
        val currentZoom = zoom
        val nextZoom = currentZoom + (scale - 1.0f)
        zoom = if (nextZoom > currentZoom) {
            minOf(nextZoom, 1.0f)
        } else {
            maxOf(nextZoom, 0.0f)
        }
    }

    private fun hasCameraPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val result = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            result == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun scalePosition(raw: Float): Int {
        val resources = resources
        val config = resources.configuration
        val dm = resources.displayMetrics
        return (raw / dm.density).toInt()
    }

    private val onGestureListener: SimpleOnGestureListener = object : SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            RNCameraViewHelper.emitTouchEvent(
                this@RNCameraView,
                false,
                scalePosition(e.x),
                scalePosition(e.y)
            )
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            RNCameraViewHelper.emitTouchEvent(
                this@RNCameraView,
                true,
                scalePosition(e.x),
                scalePosition(e.y)
            )
            return true
        }
    }
    private val onScaleGestureListener: OnScaleGestureListener = object : OnScaleGestureListener {
        override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
            onZoom(scaleGestureDetector.scaleFactor)
            return true
        }

        override fun onScaleBegin(scaleGestureDetector: ScaleGestureDetector): Boolean {
            onZoom(scaleGestureDetector.scaleFactor)
            return true
        }

        override fun onScaleEnd(scaleGestureDetector: ScaleGestureDetector) {}
    }

    init {
        mThemedReactContext.addLifecycleEventListener(this)
        addCallback(object : Callback() {
            override fun onCameraOpened(cameraView: CameraView) {
                RNCameraViewHelper.emitCameraReadyEvent(cameraView)
            }

            override fun onMountError(cameraView: CameraView) {
                RNCameraViewHelper.emitMountErrorEvent(
                    cameraView,
                    "Camera view threw an error - component could not be rendered."
                )
            }

            override fun onPictureTaken(
                cameraView: CameraView,
                data: ByteArray,
                deviceOrientation: Int
            ) {
                val promise = mPictureTakenPromises.poll()
                val options = mPictureTakenOptions.remove(promise)
                if (options!!.hasKey("fastMode") && options.getBoolean("fastMode")) {
                    promise.resolve(null)
                }
                val cacheDirectory = mPictureTakenDirectories.remove(promise)
                if (Build.VERSION.SDK_INT >= 11 /*HONEYCOMB*/) {
                    ResolveTakenPictureAsyncTask(
                        data,
                        promise,
                        options,
                        cacheDirectory,
                        deviceOrientation,
                        this@RNCameraView
                    )
                        .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                } else {
                    ResolveTakenPictureAsyncTask(
                        data,
                        promise,
                        options,
                        cacheDirectory,
                        deviceOrientation,
                        this@RNCameraView
                    )
                        .execute()
                }
                RNCameraViewHelper.emitPictureTakenEvent(cameraView)
            }

            override fun onRecordingStart(
                cameraView: CameraView,
                path: String,
                videoOrientation: Int,
                deviceOrientation: Int
            ) {
                val result = Arguments.createMap()
                startRecordingUnixTime = getCurrentUnixTime()

                result.putInt("videoOrientation", videoOrientation)
                result.putInt("deviceOrientation", deviceOrientation)
                result.putString("uri", RNFileUtils.uriFromFile(File(path)).toString())
                RNCameraViewHelper.emitRecordingStartEvent(cameraView, result)

                var torchDelayInSeconds = 1
                if (torchOnAt.compareTo(3) == 0) {
                    torchDelayInSeconds = 3
                }
                mBgHandler.postDelayed({
                    try {
                        super@RNCameraView.setFlash(FLASH_TORCH)
                        torchOnUnixTime = getCurrentUnixTime()
                    } catch (e: Exception) {
                        Log.i("TORCH_ERR_", e.toString())
                    }
                }, torchDelayInSeconds.toLong())

                var forceTorchOffDelayInSeconds = 6
                if (torchOffAt.compareTo(8) == 0) {
                    forceTorchOffDelayInSeconds = 8
                }
                mBgHandler.postDelayed({
                    try {
                        if (scanDuration.compareTo(14) > 0) {
                            super@RNCameraView.setFlash(FLASH_OFF)
                            torchOffUnixTime = getCurrentUnixTime()
                        }
                    } catch (e: Exception) {
                        Log.i("TORCH_ERR_", e.toString())
                    }
                }, forceTorchOffDelayInSeconds.toLong())
            }

            override fun onRecordingEnd(cameraView: CameraView) {
                endRecordingUnixTime = getCurrentUnixTime()
                if (scanDuration.compareTo(14) < 0) {
                    torchOffUnixTime = getCurrentUnixTime()
                }
                RNCameraViewHelper.emitRecordingEndEvent(cameraView)
            }

            override fun onVideoRecorded(
                cameraView: CameraView,
                path: String,
                videoOrientation: Int,
                deviceOrientation: Int
            ) {
                if (mVideoRecordedPromise != null) {
                    if (path != null) {
                        val result = Arguments.createMap()
                        result.putBoolean("isRecordingInterrupted", mIsRecordingInterrupted)
                        result.putInt("videoOrientation", videoOrientation)
                        result.putInt("deviceOrientation", deviceOrientation)
                        result.putString("uri", RNFileUtils.uriFromFile(File(path)).toString())

                        result.putString("recordingStart", startRecordingUnixTime.toString())
                        result.putString("recordingEnd", endRecordingUnixTime.toString())
                        result.putString("torchOn", torchOnUnixTime.toString())
                        result.putString("torchOff", torchOffUnixTime.toString())

                        mVideoRecordedPromise!!.resolve(result)
                    } else {
                        mVideoRecordedPromise!!.reject(
                            "E_RECORDING",
                            "Couldn't stop recording - there is none in progress"
                        )
                    }
                    mIsRecording = false
                    mIsRecordingInterrupted = false
                    mVideoRecordedPromise = null
                }
            }

            override fun onFramePreview(
                cameraView: CameraView,
                data: ByteArray,
                width: Int,
                height: Int,
                rotation: Int
            ) {
                val correctRotation =
                    RNCameraViewHelper.getCorrectCameraRotation(rotation, facing, cameraOrientation)
                val willCallBarCodeTask =
                    mShouldScanBarCodes && !barCodeScannerTaskLock && cameraView is BarCodeScannerAsyncTaskDelegate
                val willCallFaceTask =
                    mShouldDetectFaces && !faceDetectorTaskLock && cameraView is FaceDetectorAsyncTaskDelegate
                val willCallGoogleBarcodeTask =
                    mShouldGoogleDetectBarcodes && !googleBarcodeDetectorTaskLock && cameraView is BarcodeDetectorAsyncTaskDelegate
                val willCallTextTask =
                    mShouldRecognizeText && !textRecognizerTaskLock && cameraView is TextRecognizerAsyncTaskDelegate
                if (!willCallBarCodeTask && !willCallFaceTask && !willCallGoogleBarcodeTask && !willCallTextTask) {
                    return
                }
                if (data.size < 1.5 * width * height) {
                    return
                }
                if (willCallBarCodeTask) {
                    barCodeScannerTaskLock = true
                    val delegate = cameraView as BarCodeScannerAsyncTaskDelegate
                    BarCodeScannerAsyncTask(
                        delegate,
                        mMultiFormatReader,
                        data,
                        width,
                        height,
                        mLimitScanArea,
                        mScanAreaX,
                        mScanAreaY,
                        mScanAreaWidth,
                        mScanAreaHeight,
                        mCameraViewWidth,
                        mCameraViewHeight,
                        aspectRatio!!.toFloat()
                    ).execute()
                }
                if (willCallFaceTask) {
                    faceDetectorTaskLock = true
                    val delegate = cameraView as FaceDetectorAsyncTaskDelegate
                    FaceDetectorAsyncTask(
                        delegate,
                        mFaceDetector,
                        data,
                        width,
                        height,
                        correctRotation,
                        resources.displayMetrics.density,
                        facing,
                        getWidth(),
                        getHeight(),
                        mPaddingX,
                        mPaddingY
                    ).execute()
                }
                if (willCallGoogleBarcodeTask) {
                    googleBarcodeDetectorTaskLock = true
                    if (mGoogleVisionBarCodeMode == RNBarcodeDetector.NORMAL_MODE) {
                        invertImageData = false
                    } else if (mGoogleVisionBarCodeMode == RNBarcodeDetector.ALTERNATE_MODE) {
                        invertImageData = !invertImageData
                    } else if (mGoogleVisionBarCodeMode == RNBarcodeDetector.INVERTED_MODE) {
                        invertImageData = true
                    }
                    if (invertImageData) {
                        for (y in data.indices) {
                            data[y] = data[y].inv() as Byte
                        }
                    }
                    val delegate = cameraView as BarcodeDetectorAsyncTaskDelegate
                    BarcodeDetectorAsyncTask(
                        delegate, mGoogleBarcodeDetector, data, width, height,
                        correctRotation, resources.displayMetrics.density, facing,
                        getWidth(), getHeight(), mPaddingX, mPaddingY
                    ).execute()
                }
                if (willCallTextTask) {
                    textRecognizerTaskLock = true
                    val delegate = cameraView as TextRecognizerAsyncTaskDelegate
                    TextRecognizerAsyncTask(
                        delegate,
                        mThemedReactContext,
                        data,
                        width,
                        height,
                        correctRotation,
                        resources.displayMetrics.density,
                        facing,
                        getWidth(),
                        getHeight(),
                        mPaddingX,
                        mPaddingY
                    ).execute()
                }
            }
        })
    }
}
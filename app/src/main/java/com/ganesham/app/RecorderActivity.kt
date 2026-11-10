package com.ganesham.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.view.View
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.CameraEffect
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import android.util.Rational
import androidx.camera.effects.OverlayEffect
import android.os.Handler
import android.os.HandlerThread
import android.graphics.PorterDuff
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors

internal fun Context.applySavedAppearance() {
    val theme=getSharedPreferences("propertylens.settings",Context.MODE_PRIVATE).getString("theme","system")
    AppCompatDelegate.setDefaultNightMode(when(theme) { "light" -> AppCompatDelegate.MODE_NIGHT_NO; "dark" -> AppCompatDelegate.MODE_NIGHT_YES; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM })
}

class RecorderActivity : AppCompatActivity() {
    private var provider:ProcessCameraProvider?=null
    private var boundPreview:Preview?=null
    private lateinit var ui:LensUi
    private lateinit var compass:MagneticCompass
    private var overlay:OverlayEffect?=null
    private val overlayThread=HandlerThread("mypropertyvisit.compass.overlay").apply { start() }
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var record: Button
    private lateinit var saved: Button
    private lateinit var permission: Button
    private lateinit var store: WalkthroughStore
    private lateinit var media: RoomMediaStore
    private lateinit var pause: Button
    private lateinit var photo: Button
    private lateinit var endVisit: Button
    private lateinit var zoomButtons: LinearLayout
    private var still: ImageCapture? = null
    private var visit: CaptureVisit? = null
    private var paused = false
    private var takingPhoto = false
    private var started = false
    private var capture: VideoCapture<Recorder>? = null
    private var active: Recording? = null
    private var finalizing = false
    private var closeAfterSave = false
    private var configuring = false
    private val io = Executors.newSingleThreadExecutor()
    private val propertyId get() = intent.getStringExtra("propertyId")
    private val requestPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if(allowed) startCamera() else {
            status.text="Camera access is off. Allow it to record a walkthrough."
            permission.visibility=android.view.View.VISIBLE
        }
    }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(state: Bundle?) {
        applySavedAppearance()
        super.onCreate(state)
        ui=LensUi(this)
        store=WalkthroughStore(applicationContext)
        compass=MagneticCompass(applicationContext)
        media=RoomMediaStore(applicationContext)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        val chrome = FrameLayout(this)
        root.addView(chrome, FrameLayout.LayoutParams(-1, -1))
        chrome.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = ui.shape(0xAA000000.toInt(), 0)
        }
        header.addView(ui.iconButton(R.drawable.ic_back, "Back", Color.WHITE) { leave() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(ui.text("Record walkthrough", 18f, true, Color.WHITE), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ui.iconButton(R.drawable.ic_info, "Recording guide", Color.WHITE) {
            AlertDialog.Builder(this).setTitle("Visit recorder")
                .setMessage("Videos are silent and include the compass. Pause a recording to take a photo, then resume the same video. Captures stay together until you end the visit.")
                .setPositiveButton("OK", null).show()
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(header)
        val dial = object : View(this) {
            override fun onDraw(canvas: Canvas) {
                canvas.save()
                canvas.scale(width / 160f, height / 196f)
                CompassArtwork.draw(canvas, compass.current())
                canvas.restore()
                if (isShown) postInvalidateDelayed(150)
            }
        }.apply { contentDescription = "Magnetic compass" }
        top.addView(dial, LinearLayout.LayoutParams(dp(72), dp(88)).apply {
            leftMargin = dp(12)
            topMargin = dp(8)
        })
        chrome.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = ui.shape(0xC9000000.toInt(), 22)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        status = ui.text("Preparing camera…", 13f, true, Color.WHITE).apply {
            gravity = Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        panel.addView(status)
        permission = control("Camera access") {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } else requestPermission.launch(Manifest.permission.CAMERA)
        }.apply { visibility = View.GONE }
        panel.addView(permission)
        zoomButtons = LinearLayout(this).apply { gravity = Gravity.CENTER }
        panel.addView(zoomButtons)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        saved = control("Library") { openLibrary() }
        pause = control("Pause") {
            pause.isEnabled = false
            if (paused) active?.resume() else active?.pause()
        }.apply { visibility = View.GONE }
        photo = control("Photo") { takePhoto() }.apply { isEnabled = false }
        record = CaptureButton(this).apply {
            text = "Record"
            isEnabled = false
            stateListAnimator = null
            setOnClickListener { if (active != null) stopRecording() else recordVideo() }
        }
        controls.addView(saved, LinearLayout.LayoutParams(0, dp(56), 1f))
        controls.addView(record, LinearLayout.LayoutParams(dp(72), dp(72)))
        controls.addView(pause, LinearLayout.LayoutParams(0, dp(56), 1f))
        controls.addView(photo, LinearLayout.LayoutParams(0, dp(56), 1f))
        panel.addView(controls, LinearLayout.LayoutParams(-1, -2))
        endVisit = control("End session") {
            visit = null
            status.text = "Next capture starts a new visit"
            updateControls()
        }.apply { visibility = View.GONE }
        panel.addView(endVisit)
        chrome.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(8))
        })
        setContentView(root)
        chrome.requestApplyInsets()
        window.decorView.post { window.insetsController?.setSystemBarsAppearance(0,android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS) }
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) { override fun handleOnBackPressed() { leave() } })
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera()
        else requestPermission.launch(Manifest.permission.CAMERA)
    }
    private fun control(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setTextColor(Color.WHITE)
        setPadding(dp(4), 0, dp(4), 0)
        background = null
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun openLibrary() {
        val target = Intent(this, WalkthroughLibraryActivity::class.java).putExtra("propertyId", propertyId)
        visit?.let { target.putExtra("visitId", it.id) }
        startActivity(target)
    }

    private fun currentVisit(): CaptureVisit = visit ?: CaptureVisit.create(propertyId).also { visit = it }

    private fun updateControls() {
        val recording = active != null
        record.isEnabled = capture != null && !finalizing && !takingPhoto && (!recording || started)
        saved.isEnabled = !recording && !finalizing && !takingPhoto
        photo.isEnabled = capture != null && !finalizing && !takingPhoto && (!recording || paused)
        pause.visibility = if (recording && started && !finalizing) View.VISIBLE else View.GONE
        pause.text = if (paused) "Resume" else "Pause"
        pause.isEnabled = !takingPhoto && !finalizing
        endVisit.visibility = if (visit != null && !recording && !finalizing) View.VISIBLE else View.GONE
        endVisit.isEnabled = !takingPhoto
    }

    private fun takePhoto() {
        if (takingPhoto || finalizing || (active != null && !paused)) return
        val session = currentVisit()
        val bearing = compass.current()
        takingPhoto = true
        status.text = "Saving photo…"
        updateControls()
        val capture = still
        if (capture == null) {
            val bitmap = preview.bitmap
            if (bitmap == null) finishPhoto(Result.failure(IllegalStateException("Camera preview is not ready.")))
            else io.execute { savePhoto(bitmap, bearing, session) }
            return
        }
        val temporary = File(cacheDir, "capture-${java.util.UUID.randomUUID()}.jpg")
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(temporary).build(), io,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val image = runCatching {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(temporary)) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }
                    temporary.delete()
                    image.onSuccess { savePhoto(it, bearing, session) }
                        .onFailure { finishPhoto(Result.failure(it)) }
                }
                override fun onError(error: ImageCaptureException) {
                    temporary.delete()
                    finishPhoto(Result.failure(error))
                }
            })
    }

    private fun savePhoto(source: Bitmap, bearing: Double?, session: CaptureVisit) {
        val result = runCatching {
            val stamped = stampPhoto(source, bearing)
            try {
                media.saveVisit(session)
                media.savePhoto(stamped, propertyId, session.id)
            } finally { stamped.recycle() }
        }
        source.recycle()
        finishPhoto(result)
    }

    private fun finishPhoto(result: Result<RoomPhoto>) {
        runOnUiThread {
            takingPhoto = false
            if (isDestroyed) {
                if (active == null && !finalizing) io.shutdown()
                return@runOnUiThread
            }
            status.text = if (result.isSuccess) {
                if (paused) "Paused · Photo saved to visit" else "Photo saved to visit"
            } else "Could not save photo: ${result.exceptionOrNull()?.message}"
            updateControls()
        }
    }

    private fun startCamera() {
        if(configuring || capture!=null || isDestroyed || isFinishing) return
        configuring=true
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            configuring=false
            if(isDestroyed || isFinishing) return@addListener
            try {
                val provider=future.get()
                this.provider=provider
                check(provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) { "This device has no rear camera." }
                val viewfinder=Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).setTargetRotation(Surface.ROTATION_0).build()
                boundPreview=viewfinder
                viewfinder.setSurfaceProvider(preview.surfaceProvider)
                val recorder=Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD,FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))).build()
                val video=VideoCapture.withOutput(recorder).apply { targetRotation=Surface.ROTATION_0 }
                val effect=OverlayEffect(CameraEffect.VIDEO_CAPTURE,0,Handler(overlayThread.looper)) { failure ->
                    runOnUiThread {
                        if(!isDestroyed) { stopRecording();record.isEnabled=false;status.text="Compass overlay failed: ${failure.message}. Reopen the recorder." }
                    }
                }
                overlay=effect
                effect.setOnDrawListener { frame ->
                    val canvas=frame.overlayCanvas
                    canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR)
                    CompassPlacement.draw(canvas,frame.cropRect,frame.rotationDegrees,compass.current())
                    true
                }
                // Preview and encoder must share one sensor crop. Otherwise a
                // 4:3 camera buffer can be cropped again to 16:9 after the dial
                // has been drawn, cutting its left edge on some real phones.
                val viewport=ViewPort.Builder(Rational(9,16),Surface.ROTATION_0)
                    .setScaleType(ViewPort.FILL_CENTER).build()
                val photoCapture = ImageCapture.Builder()
                    .setTargetRotation(Surface.ROTATION_0)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                fun group(includeStill: Boolean) = UseCaseGroup.Builder().setViewPort(viewport)
                    .addUseCase(viewfinder).addUseCase(video).addEffect(effect)
                    .apply { if (includeStill) addUseCase(photoCapture) }.build()
                val camera = try {
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group(true)).also { still = photoCapture }
                } catch (_: IllegalArgumentException) {
                    provider.unbind(viewfinder, video, photoCapture)
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group(false))
                }
                camera.cameraInfo.zoomState.observe(this) { zoom ->
                    zoomButtons.removeAllViews()
                    val values = (listOf(zoom.minZoomRatio, 1f, 2f, 3f, 5f))
                        .filter { it in zoom.minZoomRatio..zoom.maxZoomRatio }.distinct().sorted()
                    values.forEach { value ->
                        val title = "${java.text.DecimalFormat("0.#").format(value)}×"
                        zoomButtons.addView(control(title) { camera.cameraControl.setZoomRatio(value) }.apply {
                            isSelected = kotlin.math.abs(zoom.zoomRatio - value) < 0.05f
                            background = ui.shape(if (isSelected) ui.action else 0x331F1729, 16)
                        }, LinearLayout.LayoutParams(dp(48), dp(44)).apply { marginEnd = dp(4) })
                    }
                }
                capture = video
                permission.visibility = View.GONE
                status.text = "Video only · No audio"
                updateControls()
            } catch(e:Exception) { status.text="Camera unavailable: ${e.message ?: "Please try reopening the recorder."}" }
        },ContextCompat.getMainExecutor(this))
    }
    private fun recordVideo() {
        val video=capture ?: return
        if(finalizing || active!=null) return
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { requestPermission.launch(Manifest.permission.CAMERA); return }
        var file:File?=null
        try {
            val target=store.prepareFile(); file=target
            val session = currentVisit()
            started = false
            paused = false
            record.isEnabled=false; saved.isEnabled=false; photo.isEnabled=false
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // No withAudioEnabled call and no microphone permission: video only.
            active=video.output.prepareRecording(this,FileOutputOptions.Builder(target).build())
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when(event) {
                        is VideoRecordEvent.Start -> {
                            started = true
                            record.text = "Stop & save"
                            status.text = "● REC · Video only"
                            status.setTextColor(Color.rgb(255,115,123))
                            updateControls()
                        }
                        is VideoRecordEvent.Pause -> {
                            paused = true
                            status.text = "Paused · Take a photo or resume"
                            status.setTextColor(Color.WHITE)
                            updateControls()
                        }
                        is VideoRecordEvent.Resume -> {
                            paused = false
                            status.text = "● REC · Video only"
                            status.setTextColor(Color.rgb(255,115,123))
                            updateControls()
                        }
                        is VideoRecordEvent.Finalize -> finishRecording(event, target, session)
                    }
                }
        } catch(e:Exception) {
            file?.delete(); active=null; finalizing=false
            resetControls(); status.text="Could not start recording: ${e.message}"
        }
    }
    private fun stopRecording() {
        if(finalizing || active==null) return
        finalizing=true; record.text="Saving…"; updateControls(); active?.stop()
    }
    private fun finishRecording(event:VideoRecordEvent.Finalize,file:File,session: CaptureVisit) {
        active?.close(); active=null; finalizing=true; started=false; paused=false
        val duration=event.recordingStats.recordedDurationNanos/1_000_000
        if(event.hasError()) {
            file.delete(); finalizing=false
            if(isDestroyed) { io.shutdown(); return }
            resetControls()
            status.text="Recording could not be saved (error ${event.error}). Please try again."
            if(closeAfterSave) finish()
            return
        }
        val store=this.store; val property=propertyId
        io.execute {
            val result = runCatching {
                media.saveVisit(session)
                store.complete(file, property, duration, compassEmbedded = true, visitId = session.id)
            }
            runOnUiThread {
                finalizing=false
                if(isDestroyed) { io.shutdown() }
                else {
                    resetControls()
                    status.text=if(result.isSuccess) "Saved offline · Open Library to play" else "Video saved, but its details could not be saved. Please check storage and try again."
                    if(!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) releaseCamera()
                    if(closeAfterSave) finish()
                }
            }
        }
    }
    private fun releaseCamera() {
        val useCases=listOfNotNull(boundPreview,capture,still)
        if(useCases.isNotEmpty()) provider?.unbind(*useCases.toTypedArray())
        capture=null;boundPreview=null;still=null
        overlay?.close();overlay=null
    }
    private fun resetControls() {
        status.setTextColor(Color.WHITE)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        record.text="Record"; updateControls()
    }
    private fun leave() { if(active!=null || finalizing) { closeAfterSave=true; stopRecording() } else finish() }
    override fun onResume() { super.onResume(); if(::compass.isInitialized) compass.start(); if(::record.isInitialized && ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera() }
    override fun onStop() {
        stopRecording()
        if(::compass.isInitialized) compass.stop()
        if(active==null && !finalizing) releaseCamera()
        super.onStop()
    }
    override fun onDestroy() {
        // Finalize may arrive after destruction, so retain the small save executor
        // until that callback has had a chance to submit its final write.
        if(active==null && !finalizing && !takingPhoto) io.shutdown()
        releaseCamera()
        overlayThread.quitSafely()
        super.onDestroy()
    }
}

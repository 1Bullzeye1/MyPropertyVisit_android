package com.ganesham.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
    private val overlayThread=HandlerThread("propertylens.compass.overlay").apply { start() }
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var record: Button
    private lateinit var saved: Button
    private lateinit var permission: Button
    private lateinit var store: WalkthroughStore
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
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(14,21,30)) }
        root.setOnApplyWindowInsetsListener { v,insets ->
            val b=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left,b.top,b.right,b.bottom); insets
        }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(6),dp(8),dp(6),dp(8)) }
        header.addView(ui.iconButton(R.drawable.ic_back,"Back",Color.WHITE) { leave() },LinearLayout.LayoutParams(dp(48),dp(48)))
        val titles=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(8),0,0,0) }
        titles.addView(ui.text("Property walkthrough",19f,true,Color.WHITE))
        titles.addView(ui.text("VIDEO ONLY · SAVED OFFLINE",10f,true,Color.rgb(170,193,211)).apply { letterSpacing=0.12f;setPadding(0,dp(3),0,0) })
        header.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(ui.iconButton(R.drawable.ic_info,"Recording guide",Color.WHITE) {
            AlertDialog.Builder(this@RecorderActivity).setTitle("Video-only recording")
                .setMessage("Records the rear camera without audio or app controls. Hold the phone upright. The compass is embedded into new videos: red points north and degrees show the camera's magnetic bearing. A slash means no reliable reading. Find offline videos under Saved, then tap Share video to send one using your chosen app.")
                .setPositiveButton("Got it",null).show()
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(header)
        preview=PreviewView(this).apply { implementationMode=PreviewView.ImplementationMode.COMPATIBLE; scaleType=PreviewView.ScaleType.FIT_CENTER }
        root.addView(preview,LinearLayout.LayoutParams(-1,0,1f))
        status=TextView(this).apply { text="Preparing camera…"; textSize=14f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setPadding(dp(14),dp(8),dp(14),dp(8)); accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        root.addView(status)
        permission=Button(this).apply { text="Camera access"; isAllCaps=false; visibility=android.view.View.GONE; setOnClickListener {
            if(!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))
            else requestPermission.launch(Manifest.permission.CAMERA)
        } }
        root.addView(permission)
        val controls=LinearLayout(this).apply { gravity=Gravity.CENTER;setPadding(dp(20),dp(4),dp(20),dp(16)) }
        saved=Button(this).apply {
            text="Saved";isAllCaps=false;textSize=12f;setTextColor(Color.WHITE);background=null;stateListAnimator=null
            val icon=androidx.appcompat.content.res.AppCompatResources.getDrawable(this@RecorderActivity,R.drawable.ic_folder)?.mutate()?.apply { setTint(Color.WHITE);setBounds(0,0,dp(25),dp(25)) }
            setCompoundDrawables(null,icon,null,null);compoundDrawablePadding=dp(6)
            setOnClickListener { startActivity(Intent(this@RecorderActivity,WalkthroughLibraryActivity::class.java).putExtra("propertyId",propertyId)) }
        }
        controls.addView(saved,LinearLayout.LayoutParams(0,dp(76),1f))
        record=CaptureButton(this).apply { text="Record";isEnabled=false;stateListAnimator=null;setOnClickListener { if(active!=null) stopRecording() else recordVideo() } }
        controls.addView(record,LinearLayout.LayoutParams(dp(88),dp(88)))
        controls.addView(ui.text("HD\nVideo only",12f,false,Color.rgb(170,193,211)).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(controls)
        setContentView(root); root.requestApplyInsets()
        window.decorView.post { window.insetsController?.setSystemBarsAppearance(0,android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS) }
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) { override fun handleOnBackPressed() { leave() } })
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera()
        else requestPermission.launch(Manifest.permission.CAMERA)
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
                val effect=OverlayEffect(CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,0,Handler(overlayThread.looper)) { failure ->
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
                val group=UseCaseGroup.Builder().setViewPort(viewport)
                    .addUseCase(viewfinder).addUseCase(video).addEffect(effect).build()
                provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,group)
                capture=video; record.isEnabled=true; permission.visibility=android.view.View.GONE
                status.text="Video only · No audio"
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
            record.isEnabled=false; saved.isEnabled=false
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // No withAudioEnabled call and no microphone permission: video only.
            active=video.output.prepareRecording(this,FileOutputOptions.Builder(target).build())
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when(event) {
                        is VideoRecordEvent.Start -> { record.text="Stop & save"; record.isEnabled=true; status.text="● REC · Video only";status.setTextColor(Color.rgb(255,115,123)) }
                        is VideoRecordEvent.Finalize -> finishRecording(event,target)
                    }
                }
        } catch(e:Exception) {
            file?.delete(); active=null; finalizing=false
            resetControls(); status.text="Could not start recording: ${e.message}"
        }
    }
    private fun stopRecording() {
        if(finalizing || active==null) return
        finalizing=true; record.isEnabled=false; record.text="Saving…"; active?.stop()
    }
    private fun finishRecording(event:VideoRecordEvent.Finalize,file:File) {
        active?.close(); active=null; finalizing=true
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
            val result=runCatching { store.complete(file,property,duration,compassEmbedded=true) }
            runOnUiThread {
                finalizing=false
                if(isDestroyed) { io.shutdown() }
                else {
                    resetControls()
                    status.text=if(result.isSuccess) "Saved offline · Open Saved to play" else "Video saved, but its details could not be saved. Please check storage and try again."
                    if(!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) releaseCamera()
                    if(closeAfterSave) finish()
                }
            }
        }
    }
    private fun releaseCamera() {
        val useCases=listOfNotNull(boundPreview,capture)
        if(useCases.isNotEmpty()) provider?.unbind(*useCases.toTypedArray())
        capture=null;boundPreview=null
        overlay?.close();overlay=null
    }
    private fun resetControls() {
        status.setTextColor(Color.WHITE)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        record.text="Record"; record.isEnabled=capture!=null; saved.isEnabled=true
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
        if(active==null && !finalizing) io.shutdown()
        releaseCamera()
        overlayThread.quitSafely()
        super.onDestroy()
    }
}

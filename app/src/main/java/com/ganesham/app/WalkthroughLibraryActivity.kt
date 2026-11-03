package com.ganesham.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowInsets
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class WalkthroughLibraryActivity:AppCompatActivity() {
    private lateinit var store:WalkthroughStore
    private val io=Executors.newSingleThreadExecutor()
    private var player:VideoView?=null
    private var playing:String?=null
    private var position=0
    private val propertyId get()=intent.getStringExtra("propertyId")
    override fun onCreate(state:Bundle?) {
        applySavedAppearance(); super.onCreate(state)
        store=WalkthroughStore(applicationContext)
        playing=state?.getString("playing") ?: intent.getStringExtra("clipId"); position=state?.getInt("position") ?: 0
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { back() }
        })
        if(playing!=null) play(playing!!) else showLibrary()
    }
    private fun layout(title:String):LinearLayout {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,0,24,0) }
        root.setOnApplyWindowInsetsListener { v,insets ->
            val b=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left+24,b.top,b.right+24,b.bottom); insets
        }
        root.addView(Button(this).apply { text="‹ Back"; isAllCaps=false; setOnClickListener { back() } })
        root.addView(TextView(this).apply { text=title; textSize=24f; setPadding(12,20,12,24) })
        setContentView(root); root.requestApplyInsets();return root
    }
    private fun showLibrary() {
        player?.stopPlayback(); player=null; playing=null; position=0
        val root=layout("Saved walkthroughs")
        val scroll=ScrollView(this)
        val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val filter=propertyId
        io.execute {
            val result=runCatching { store.all(filter) }
            runOnUiThread {
                if(isDestroyed || playing!=null) return@runOnUiThread
                if(result.isFailure) list.addView(TextView(this).apply { text="Could not load recordings. Please reopen this screen." })
                else {
                    val clips=result.getOrThrow()
                    if(clips.isEmpty()) list.addView(TextView(this).apply { text="No recordings yet. Saved walkthroughs will be available here offline."; textSize=16f; setPadding(12,24,12,24) })
                    clips.forEach { clip ->
                        list.addView(Button(this).apply {
                            text="▶  ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(clip.created))} · ${clip.durationMillis/1000}s"
                            isAllCaps=false; gravity=Gravity.START or Gravity.CENTER_VERTICAL; minHeight=72
                            setOnClickListener { position=0; play(clip.id) }
                        })
                    }
                }
            }
        }
    }
    private fun play(id:String) {
        val file=runCatching { store.movie(id) }.getOrNull()
        if(file==null || !file.isFile) { showLibrary(); return }
        playing=id
        val root=layout("Walkthrough")
        val container=FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val view=VideoView(this)
        val loading=ProgressBar(this).apply { tag="videoLoading" }
        container.addView(view,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        container.addView(loading,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER))
        player=view;root.addView(container,LinearLayout.LayoutParams(-1,0,1f))
        view.setOnInfoListener { _,what,_ ->
            if(what==android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) loading.visibility=android.view.View.GONE
            false
        }
        view.setMediaController(MediaController(this).apply { setAnchorView(view) })
        view.setOnPreparedListener { view.seekTo(position); view.start() }
        view.setOnErrorListener { _,_,_ ->
            loading.visibility=android.view.View.GONE
            android.app.AlertDialog.Builder(this).setTitle("Playback unavailable").setMessage("This video cannot be played on this device.").setPositiveButton("OK") { _,_ -> showLibrary() }.show();true
        }
        view.setVideoPath(file.absolutePath)
    }
    private fun back() { if(playing!=null) showLibrary() else finish() }
    override fun onSaveInstanceState(out:Bundle) { out.putString("playing",playing);out.putInt("position",player?.currentPosition ?: position);super.onSaveInstanceState(out) }
    override fun onPause() { position=player?.currentPosition ?: position;player?.pause();super.onPause() }
    override fun onDestroy() { player?.stopPlayback();io.shutdown();super.onDestroy() }
}

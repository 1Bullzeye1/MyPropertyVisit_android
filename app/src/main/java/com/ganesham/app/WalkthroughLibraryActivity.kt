package com.ganesham.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class WalkthroughLibraryActivity:AppCompatActivity() {
    private lateinit var store:WalkthroughStore
    private lateinit var ui:LensUi
    private lateinit var mediaStore:RoomMediaStore
    private val visitId get() = intent.getStringExtra("visitId")
    private val io=Executors.newSingleThreadExecutor()
    private var player:VideoView?=null
    private var playing:String?=null
    private var position=0
    private var generation=0
    private val propertyId get()=intent.getStringExtra("propertyId")
    override fun onCreate(state:Bundle?) {
        applySavedAppearance();super.onCreate(state)
        ui=LensUi(this);store=WalkthroughStore(applicationContext);mediaStore=RoomMediaStore(applicationContext)
        playing=state?.getString("playing") ?: intent.getStringExtra("clipId");position=state?.getInt("position") ?: 0
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) { override fun handleOnBackPressed() { back() } })
        if(playing!=null) play(playing!!) else showLibrary()
    }
    private fun layout(title:String):LinearLayout {
        generation++
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(ui.background) }
        root.setOnApplyWindowInsetsListener { v,insets ->
            val b=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left,b.top,b.right,b.bottom);insets
        }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(8),ui.dp(8),ui.dp(16),ui.dp(8)) }
        header.addView(ui.iconButton(R.drawable.ic_back,"Back") { back() },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        header.addView(ui.text(title,23f,true),LinearLayout.LayoutParams(0,-2,1f))
        root.addView(header)
        setContentView(root);root.requestApplyInsets();return root
    }
    private fun showLibrary() {
        player?.stopPlayback();player=null;playing=null;position=0
        val root = layout(if (visitId == null) "Photos & videos" else "Visit library")
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(24)) }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        list.addView(ui.hero(if (visitId == null) "Your media library" else "Visit library", "Browse saved walkthroughs, room photos and measurements."))
        list.addView(ui.menuCard("Room photos & dimensions", "View photos and edit room details", R.drawable.ic_folder) {
            startActivity(Intent(this, RoomPhotosActivity::class.java).putExtra("propertyId", propertyId).putExtra("visitId", visitId))
        })
        if (visitId == null) {
            list.addView(ui.menuCard("Record walkthrough", "Capture photos and video with compass", R.drawable.ic_video) {
                startActivity(Intent(this, RecorderActivity::class.java).putExtra("propertyId", propertyId))
            })
        }
        val screen = generation
        val loading = ui.text("Loading media…", 14f, false, ui.muted)
        list.addView(loading)
        io.execute {
            val result = runCatching {
                val clips = store.all(propertyId, visitId)
                val visits = if (visitId == null) {
                    val populated = (clips.mapNotNull { it.visitId } + mediaStore.all(propertyId).mapNotNull { it.visitId }).toSet()
                    mediaStore.visits(propertyId).filter { it.id in populated }
                } else emptyList()
                clips to visits
            }
            runOnUiThread {
                if (isDestroyed || generation != screen) return@runOnUiThread
                list.removeView(loading)
                result.onFailure { list.addView(ui.text("Could not load media. Please reopen this screen.")) }
                result.onSuccess { (clips, visits) ->
                    if (visitId == null) {
                        list.addView(ui.text("Sessions", 18f, true))
                        if (visits.isEmpty()) list.addView(ui.text("New captures will be grouped by visit.", 14f, false, ui.muted))
                        visits.forEach { visit ->
                            list.addView(ui.menuCard(DateFormat.getDateTimeInstance().format(Date(visit.created)), "Photos and videos from this visit", R.drawable.ic_folder) {
                                startActivity(Intent(this, WalkthroughLibraryActivity::class.java)
                                    .putExtra("propertyId", visit.propertyId).putExtra("visitId", visit.id))
                            })
                        }
                    }
                    list.addView(ui.text(if (visitId == null) "All saved videos" else "Session videos", 18f, true))
                    if (clips.isEmpty()) list.addView(ui.text("No videos saved yet.", 14f, false, ui.muted))
                    clips.forEach { addCard(list, it, screen) }
                }
            }
        }
    }

    private fun addCard(list:LinearLayout,clip:SavedWalkthrough,screen:Int) {
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            background=ui.shape(ui.surface,22);setPadding(ui.dp(12),ui.dp(12),ui.dp(8),ui.dp(12))
            isClickable=true;isFocusable=true;contentDescription="Play walkthrough from ${DateFormat.getDateTimeInstance().format(Date(clip.created))}"
            setOnClickListener { position=0;play(clip.id) }
        }
        val thumbnail=ImageView(this).apply {
            scaleType=ImageView.ScaleType.CENTER_CROP;background=ui.shape(Color.BLACK,14);clipToOutline=true
            setImageResource(R.drawable.ic_play);imageTintList=android.content.res.ColorStateList.valueOf(Color.WHITE)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        card.addView(thumbnail,LinearLayout.LayoutParams(ui.dp(82),ui.dp(108)))
        val words=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(ui.dp(14),0,ui.dp(4),0) }
        words.addView(ui.text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(clip.created)),16f,true))
        words.addView(ui.text("${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(clip.created))} · ${formatDuration(clip.durationMillis)}",13f,false,ui.muted).apply { setPadding(0,ui.dp(6),0,ui.dp(12)) })
        words.addView(ui.text(if(clip.compassEmbedded) "Compass included" else "Video only",11f,true,ui.accent))
        card.addView(words,LinearLayout.LayoutParams(0,-2,1f))
        card.addView(ui.iconButton(R.drawable.ic_share,"Share walkthrough") { share(clip.id) },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        list.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=ui.dp(14) })
        io.execute {
            val media=MediaMetadataRetriever()
            val bitmap=try { media.setDataSource(store.movie(clip.id).absolutePath);media.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,180,240) } catch(_:Exception) { null } finally { media.release() }
            if(bitmap!=null) runOnUiThread {
                if(isDestroyed || generation!=screen) bitmap.recycle()
                else { thumbnail.imageTintList=null;thumbnail.setImageBitmap(bitmap) }
            }
        }
    }
    private fun play(id:String) {
        val file=runCatching { store.movie(id) }.getOrNull()
        if(file==null || !file.isFile) { showLibrary();return }
        player?.stopPlayback();playing=id
        val root=layout("Walkthrough")
        val container=FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val view=VideoView(this)
        val loading=ProgressBar(this).apply { tag="videoLoading" }
        container.addView(view,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        container.addView(loading,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER))
        player=view;root.addView(container,LinearLayout.LayoutParams(-1,0,1f))
        val footer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(ui.dp(20),ui.dp(16),ui.dp(20),ui.dp(20)) }
        footer.addView(ui.action("Share video",R.drawable.ic_share) { share(id) },LinearLayout.LayoutParams(-1,ui.dp(56)))
        footer.addView(ui.action("Capture room photo", R.drawable.ic_folder) {
            view.pause()
            saveFrame(id, view.currentPosition.toLong())
        }, LinearLayout.LayoutParams(-1, ui.dp(52)).apply { topMargin = ui.dp(8) })
        footer.addView(ui.text("Shares the saved video as recorded, including any embedded compass.",12f,false,ui.muted).apply { gravity=Gravity.CENTER;setPadding(ui.dp(8),ui.dp(10),ui.dp(8),0) })
        root.addView(footer)
        view.setOnInfoListener { _,what,_ -> if(what==android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) loading.visibility=View.GONE;false }
        view.setMediaController(MediaController(this).apply { setAnchorView(view) })
        view.setOnPreparedListener { view.seekTo(position);view.start() }
        view.setOnErrorListener { _,_,_ ->
            loading.visibility=View.GONE
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Playback unavailable").setMessage("This video cannot be played on this device.").setPositiveButton("OK") { _,_ -> showLibrary() }.show();true
        }
        view.setVideoPath(file.absolutePath)
    }
    private var capturingFrame = false
    private fun saveFrame(id: String, millis: Long) {
        if (capturingFrame) return
        capturingFrame = true
        val screen = generation
        io.execute {
            val result = runCatching {
                val clip = store.all().first { it.id == id }
                val time = millis.coerceIn(0, (clip.durationMillis - 1).coerceAtLeast(0))
                val media = MediaMetadataRetriever()
                val source = try {
                    media.setDataSource(store.movie(id).absolutePath)
                    media.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: error("No frame available.")
                } finally { media.release() }
                try {
                    if (clip.compassEmbedded) mediaStore.savePhoto(source, clip.propertyId, clip.visitId, id, time)
                    else {
                        val stamped = stampPhoto(source, null)
                        try { mediaStore.savePhoto(stamped, clip.propertyId, clip.visitId, id, time) }
                        finally { stamped.recycle() }
                    }
                } finally { source.recycle() }
            }
            runOnUiThread {
                capturingFrame = false
                if (isDestroyed || screen != generation) return@runOnUiThread
                result.onSuccess { photo ->
                    startActivity(Intent(this, RoomPhotosActivity::class.java).putExtra("clipId", id).putExtra("editPhotoId", photo.id))
                }.onFailure { Toast.makeText(this, "Could not capture a room photo.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun share(id:String) {
        player?.pause()
        try { startActivity(Intent.createChooser(WalkthroughSharing.intent(this,id),"Share walkthrough")) }
        catch(e:Exception) { androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Unable to share").setMessage(e.message ?: "Please try again.").setPositiveButton("OK",null).show() }
    }
    private fun formatDuration(ms:Long):String { val seconds=ms/1000;return "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}" }
    private fun back() { if(playing!=null) showLibrary() else finish() }
    override fun onSaveInstanceState(out:Bundle) { out.putString("playing",playing);out.putInt("position",player?.currentPosition ?: position);super.onSaveInstanceState(out) }
    override fun onRestart() {
        super.onRestart()
        if (playing == null) showLibrary()
    }
    override fun onPause() { position=player?.currentPosition ?: position;player?.pause();super.onPause() }
    override fun onDestroy() { player?.stopPlayback();io.shutdown();super.onDestroy() }
}

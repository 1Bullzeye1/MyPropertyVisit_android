package com.ganesham.app

import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowInsets
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MediaExportActivity : AppCompatActivity() {
    private data class Item(val file: File, val video: Boolean, val title: String)
    private val io = Executors.newSingleThreadExecutor()
    private val items = mutableListOf<Item>()
    private val selected = mutableSetOf<Int>()
    private val checks = mutableListOf<CheckBox>()
    private var busy = false
    private lateinit var ui: LensUi
    private lateinit var status: TextView
    private lateinit var save: Button
    private lateinit var share: Button
    private lateinit var all: Button
    override fun onCreate(state: Bundle?) {
        applySavedAppearance(); super.onCreate(state)
        ui = LensUi(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(ui.background)
            setOnApplyWindowInsetsListener { view, insets ->
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(b.left, b.top, b.right, b.bottom); insets
            }
        }
        root.addView(ui.iconButton(R.drawable.ic_back, "Back") { if (!busy) finish() })
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(20)) }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(ui.hero("Share & save", "Choose media to share or save to your phone's MyPropertyVisit album. Embedded compasses are preserved. Room details remain in the app."))
        status = ui.text("Loading media…", 14f, false, ui.muted); content.addView(status)
        all = Button(this).apply { text = "Select all"; setOnClickListener {
            val select = selected.size != items.size
            checks.forEach { it.isChecked = select }
        } }; content.addView(all)
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; content.addView(rows)
        save = Button(this).apply { text = "Save selected to Gallery"; setOnClickListener { export(true) } }; content.addView(save)
        share = Button(this).apply { text = "Share selected"; setOnClickListener { export(false) } }; content.addView(share)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!busy) finish() }
        })
        setContentView(root); root.requestApplyInsets(); update()
        io.execute {
            val result = runCatching {
                val photos = RoomMediaStore(applicationContext)
                val videos = WalkthroughStore(applicationContext)
                val property = intent.getStringExtra("propertyId")
                val visit = intent.getStringExtra("visitId")
                val photoId = intent.getStringExtra("photoId")
                val videoId = intent.getStringExtra("videoId")
                val photosOnly = intent.getBooleanExtra("photosOnly", false)
                val clip = intent.getStringExtra("clipId")
                val p = if (videoId != null) emptyList() else photos.all(property, visit, clip)
                    .filter { photoId == null || it.id == photoId }
                    .map { Item(photos.image(it.id), false, it.title) }
                val v = if (photoId != null || photosOnly) emptyList() else videos.all(property, visit)
                    .filter { videoId == null || it.id == videoId }
                    .map { Item(videos.movie(it.id), true, "Video · " + DateFormat.getDateTimeInstance().format(Date(it.created))) }
                p + v
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                result.onSuccess { loaded ->
                    items.addAll(loaded)
                    loaded.forEachIndexed { index, item ->
                        selected.add(index)
                        val check = CheckBox(this).apply {
                            text = item.title; setTextColor(ui.ink); isChecked = true
                            setOnCheckedChangeListener { _, value ->
                                if (value) selected.add(index) else selected.remove(index)
                                update()
                            }
                        }
                        checks.add(check); rows.addView(check)
                    }
                    status.text = if (loaded.isEmpty()) "No media to export." else "${loaded.size} items available"
                    update()
                }.onFailure { status.text = "Could not load media. Reopen this screen to retry." }
            }
        }
    }
    private fun update() {
        save.isEnabled = !busy && selected.isNotEmpty()
        share.isEnabled = !busy && selected.isNotEmpty()
        all.isEnabled = !busy && items.isNotEmpty()
        all.text = if (selected.size == items.size && items.isNotEmpty()) "Deselect all" else "Select all"
        checks.forEach { it.isEnabled = !busy }
    }
    private fun export(toGallery: Boolean) {
        if (busy || selected.isEmpty()) return
        val chosen = selected.sorted().map { items[it] }
        busy = true; update()
        io.execute {
            val successful = mutableListOf<Int>()
            val uris = arrayListOf<android.net.Uri>()
            chosen.forEachIndexed { index, item ->
                runOnUiThread { if (!isDestroyed) status.text = "Preparing ${index + 1} of ${chosen.size}…" }
                runCatching {
                    check(item.file.isFile && item.file.length() > 0) { "Media unavailable" }
                    if (toGallery) saveMediaToGallery(applicationContext, item.file, item.video)
                    else uris.add(FileProvider.getUriForFile(this, "$packageName.walkthroughs", item.file))
                }.onSuccess { successful.add(index) }
            }
            runOnUiThread {
                busy = false
                if (isDestroyed) return@runOnUiThread
                update()
                val failed = chosen.size - successful.size
                status.text = if (toGallery) "Saved ${successful.size} of ${chosen.size} items to Gallery." else "Prepared ${successful.size} of ${chosen.size} items."
                if (failed > 0) {
                    // Keep only failed items selected so retry does not duplicate successful saves.
                    if (toGallery) {
                        val done = successful.map { chosen[it].file }.toSet()
                        checks.forEachIndexed { i, check -> if (items[i].file in done) check.isChecked = false }
                    }
                    Toast.makeText(this, "$failed items failed. You can retry those items.", Toast.LENGTH_LONG).show()
                }
                if (!toGallery && uris.isNotEmpty()) {
                    val kind = if (chosen.all { it.video }) "video/mp4" else if (chosen.none { it.video }) "image/jpeg" else "*/*"
                    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                        type = kind
                        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0]) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        clipData = ClipData.newUri(contentResolver, "MyPropertyVisit media", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try { startActivity(Intent.createChooser(intent, "Share MyPropertyVisit media")) }
                    catch (_: Exception) { AlertDialog.Builder(this).setMessage("No app is available to share this media.").setPositiveButton("OK", null).show() }
                }
            }
        }
    }
    override fun onDestroy() { io.shutdown(); super.onDestroy() }
}

internal fun saveMediaToGallery(context: android.content.Context, file: File, video: Boolean): android.net.Uri {
        val contentResolver = context.contentResolver
        check(file.isFile && file.length() > 0) { "Media unavailable" }
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "MyPropertyVisit-${java.util.UUID.randomUUID()}.${if (video) "mp4" else "jpg"}")
            put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/MyPropertyVisit")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(collection, values) ?: error("Cannot create gallery item")
        try {
            contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("Cannot save media")
            check(contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
            return uri
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null); throw error
        }
    }

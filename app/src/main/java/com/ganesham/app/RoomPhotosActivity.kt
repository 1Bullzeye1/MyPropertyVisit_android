package com.ganesham.app

import android.app.Dialog
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.DecimalFormatSymbols
import java.util.UUID
import java.util.concurrent.Executors

class RoomPhotosActivity : AppCompatActivity() {
    private lateinit var ui: LensUi
    private lateinit var store: RoomMediaStore
    private lateinit var list: LinearLayout
    private val io = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var viewer: Dialog? = null
    private var editOnLoad: String? = null

    override fun onCreate(state: Bundle?) {
        applySavedAppearance()
        super.onCreate(state)
        ui = LensUi(this)
        store = RoomMediaStore(applicationContext)
        editOnLoad = if (state == null) intent.getStringExtra("editPhotoId") else null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.background)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ui.iconButton(R.drawable.ic_back, "Back") { finish() }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        header.addView(ui.text("Room photos", 23f, true))
        root.addView(header)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(24))
        }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        root.requestApplyInsets()
        load()
    }

    private fun load() {
        val screen = ++generation
        list.removeAllViews()
        list.addView(ui.hero("Room gallery", "Open a photo full screen or add measurements and room details."))
        list.addView(ProgressBar(this), LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)).apply { gravity = Gravity.CENTER })
        io.execute {
            val result = runCatching {
                store.all(intent.getStringExtra("propertyId"), intent.getStringExtra("visitId"), intent.getStringExtra("clipId"))
            }
            runOnUiThread {
                if (isDestroyed || generation != screen) return@runOnUiThread
                list.removeViewAt(1)
                result.onFailure { list.addView(ui.text("Could not load room photos.")) }
                result.onSuccess { photos ->
                    if (photos.isEmpty()) {
                        list.addView(ui.menuCard("No photos yet", "Take a photo in the recorder or save a frame from a walkthrough.", R.drawable.ic_folder))
                    }
                    photos.forEach { addPhoto(it, screen) }
                    val pending = editOnLoad
                    editOnLoad = null
                    photos.firstOrNull { it.id == pending }?.let { edit(it) }
                }
            }
        }
    }

    private fun addPhoto(photo: RoomPhoto, screen: Int) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.shape(ui.surface, 22)
            setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(8))
        }
        val thumbnail = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = ui.shape(ui.background, 14)
            contentDescription = "View ${photo.title} full screen"
            isFocusable = true
            setOnClickListener { showPhoto(photo) }
        }
        card.addView(thumbnail, LinearLayout.LayoutParams(-1, ui.dp(215)))
        card.addView(ui.text(photo.title, 18f, true).apply { setPadding(0, ui.dp(10), 0, 0) })
        photo.room?.let { card.addView(ui.text(it.type, 13f, true, ui.accent)) }
        card.addView(ui.text(photo.room?.dimensions() ?: "Add measurements later", 14f, false, ui.muted))
        photo.sourceMillis?.let { millis ->
            val seconds = millis / 1000
            card.addView(ui.text("From video at ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", 12f, false, ui.muted))
        }
        photo.room?.notes?.takeIf { it.isNotBlank() }?.let { card.addView(ui.text(it, 14f, false, ui.muted)) }
        val controls = LinearLayout(this)
        controls.addView(Button(this).apply {
            text = "Edit details"
            isAllCaps = false
            setOnClickListener { edit(photo) }
        }, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
        controls.addView(Button(this).apply {
            text = "Delete"
            isAllCaps = false
            setOnClickListener {
                AlertDialog.Builder(this@RoomPhotosActivity).setTitle("Delete photo?")
                    .setMessage("Delete this photo from PropertyLens? Other photos in the room will stay saved.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                        io.execute {
                            val result = runCatching { store.delete(photo) }
                            runOnUiThread { if (!isDestroyed) result.onSuccess { load() }.onFailure { message("Could not delete photo.") } }
                        }
                    }.show()
            }
        }, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
        card.addView(controls)
        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })
        io.execute {
            val bitmap = decode(photo, 640)
            runOnUiThread {
                if (isDestroyed || generation != screen) bitmap?.recycle()
                else if (bitmap != null) thumbnail.setImageBitmap(bitmap)
            }
        }
    }

    private fun showPhoto(photo: RoomPhoto) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = photo.title }
        root.addView(image, FrameLayout.LayoutParams(-1, -1))
        val close = Button(this).apply {
            text = "×"
            textSize = 24f
            contentDescription = "Close photo"
            setTextColor(Color.WHITE)
            background = ui.shape(0xB0000000.toInt(), 22)
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(close, FrameLayout.LayoutParams(ui.dp(48), ui.dp(48), Gravity.TOP or Gravity.END).apply {
            topMargin = ui.dp(12); marginEnd = ui.dp(12)
        })
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        dialog.setContentView(root)
        viewer = dialog
        dialog.setOnDismissListener { viewer = null }
        dialog.show()
        root.requestApplyInsets()
        io.execute {
            val bitmap = decode(photo, maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels))
            runOnUiThread {
                if (!dialog.isShowing) bitmap?.recycle()
                else if (bitmap == null) message("Photo unavailable.")
                else image.setImageBitmap(bitmap)
            }
        }
    }

    private fun decode(photo: RoomPhoto, limit: Int) = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(store.image(photo.id))) { decoder, info, _ ->
            val size = info.size
            val scale = minOf(1.0, limit.toDouble() / maxOf(size.width, size.height))
            decoder.setTargetSize((size.width * scale).toInt().coerceAtLeast(1), (size.height * scale).toInt().coerceAtLeast(1))
        }
    }.getOrNull()

    private fun edit(photo: RoomPhoto) {
        io.execute {
            val result = runCatching { store.rooms(photo.scope) }
            runOnUiThread {
                if (!isDestroyed) result.onSuccess { showEditor(photo, it) }.onFailure { message("Could not load rooms.") }
            }
        }
    }

    private fun showEditor(photo: RoomPhoto, rooms: List<RoomDetails>) {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(16))
        }
        fun field(label: String, decimal: Boolean = false) = EditText(this).apply {
            hint = label
            setSingleLine()
            if (decimal) inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            fields.addView(this)
        }
        fun choices(values: List<String>) = Spinner(this).apply {
            adapter = ArrayAdapter(this@RoomPhotosActivity, android.R.layout.simple_spinner_dropdown_item, values)
            fields.addView(this)
        }
        fields.addView(ui.text("Photos assigned to the same room share measurements and notes.", 14f, false, ui.muted))
        fields.addView(ui.text("Room", 14f, true))
        val roomChoice = choices(listOf("New room") + rooms.map { "${it.type} · ${it.name}" })
        val name = field("Room name")
        val types = listOf("Bedroom", "Living room", "Kitchen", "Bathroom", "Balcony", "Plot", "Building", "Other")
        val type = choices(types)
        val unit = choices(listOf("Metres", "Feet"))
        val length = field("Length (optional)", true)
        val width = field("Width (optional)", true)
        val height = field("Height (optional)", true)
        val notes = field("Notes")
        val initial = rooms.indexOfFirst { it.id == photo.room?.id } + 1
        var selected = initial
        var displayedUnit = if (photo.room?.feet == true) 1 else 0
        fun parse(text: String): Double? {
            val separator = DecimalFormatSymbols.getInstance().decimalSeparator
            return text.trim().replace(separator, '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        }
        fun display(value: Double?): String = value?.let {
            java.math.BigDecimal.valueOf(it).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }.orEmpty()
        fun fill(room: RoomDetails?) {
            name.setText(room?.name.orEmpty())
            type.setSelection(types.indexOf(room?.type).coerceAtLeast(0))
            displayedUnit = if (room?.feet == true) 1 else 0
            unit.setSelection(displayedUnit)
            val factor = if (displayedUnit == 1) 0.3048 else 1.0
            length.setText(display(room?.lengthMetres?.div(factor)))
            width.setText(display(room?.widthMetres?.div(factor)))
            height.setText(display(room?.heightMetres?.div(factor)))
            notes.setText(room?.notes.orEmpty())
        }
        roomChoice.setSelection(initial)
        fill(photo.room)
        fun onChoice(spinner: Spinner, change: (Int) -> Unit) {
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = change(position)
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        onChoice(roomChoice) { position ->
            if (position != selected) {
                selected = position
                if (position > 0) fill(rooms[position - 1])
            }
        }
        onChoice(unit) { position ->
            if (position != displayedUnit) {
                val factor = if (position == 1) 1.0 / 0.3048 else 0.3048
                listOf(length, width, height).forEach { field ->
                    parse(field.text.toString())?.let { field.setText(display(it * factor)) }
                }
                displayedUnit = position
            }
        }
        val dialog = AlertDialog.Builder(this).setTitle("Room details")
            .setView(ScrollView(this).apply { addView(fields) })
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                val roomName = name.text.toString().trim()
                if (roomName.isBlank() || roomName.length > 80) { name.error = "Enter a room name (1–80 characters)"; return@setOnClickListener }
                val measurements = listOf(length, width, height).map { parse(it.text.toString()) }
                val invalid = listOf(length, width, height).zip(measurements).firstOrNull { (input, value) -> input.text.isNotBlank() && value == null }
                if (invalid != null) { invalid.first.error = "Enter a positive measurement"; return@setOnClickListener }
                if ((measurements[0] == null) != (measurements[1] == null)) {
                    (if (measurements[0] == null) length else width).error = "Enter both length and width"
                    return@setOnClickListener
                }
                val factor = if (displayedUnit == 1) 0.3048 else 1.0
                val room = RoomDetails(rooms.getOrNull(selected - 1)?.id ?: UUID.randomUUID().toString(), photo.scope,
                    types[type.selectedItemPosition], roomName, measurements[0]?.times(factor), measurements[1]?.times(factor),
                    measurements[2]?.times(factor), displayedUnit == 1, notes.text.toString().trim())
                save.isEnabled = false
                io.execute {
                    val result = runCatching { store.assign(photo, room) }
                    runOnUiThread {
                        if (!isDestroyed) result.onSuccess { dialog.dismiss(); load() }
                            .onFailure { save.isEnabled = true; message("Could not save room details.") }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    override fun onDestroy() { viewer?.dismiss(); io.shutdown(); super.onDestroy() }
}

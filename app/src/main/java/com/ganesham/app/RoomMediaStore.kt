package com.ganesham.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class CaptureVisit(val id: String, val propertyId: String?, val created: Long) {
    companion object {
        fun create(propertyId: String?) = CaptureVisit(UUID.randomUUID().toString(), propertyId, System.currentTimeMillis())
    }
}

internal data class RoomDetails(
    val id: String, val scope: String, val type: String, val name: String,
    val lengthMetres: Double?, val widthMetres: Double?, val heightMetres: Double?,
    val feet: Boolean, val notes: String
) {
    fun dimensions(): String {
        val unit = if (feet) "ft" else "m"
        val factor = if (feet) 0.3048 else 1.0
        fun number(value: Double) = java.text.NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 2
            isGroupingUsed = false
        }.format(value)
        val parts = mutableListOf<String>()
        if (lengthMetres != null && widthMetres != null) {
            parts.add("${number(lengthMetres / factor)} × ${number(widthMetres / factor)} $unit")
            parts.add("${number(lengthMetres * widthMetres / (factor * factor))} $unit²")
        }
        if (heightMetres != null) parts.add("Height ${number(heightMetres / factor)} $unit")
        return parts.joinToString(" · ").ifBlank { "Dimensions not entered" }
    }
}

internal data class RoomPhoto(
    val id: String, val created: Long, val propertyId: String?, val visitId: String?,
    val clipId: String?, val sourceMillis: Long?, val room: RoomDetails?
) {
    val scope: String get() = propertyId?.let { "property:$it" } ?: clipId?.let { "video:$it" } ?: "camera"
    val title: String get() = room?.name ?: "Uncategorised photo"
}

internal class RoomMediaStore(context: Context) {
    private val root = File(context.filesDir, "room-media")
    private val photos = AtomicFile(File(root, "photos.json"))
    private val visits = AtomicFile(File(root, "visits.json"))

    private fun read(file: AtomicFile): JSONObject =
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) JSONObject(file.readFully().toString(Charsets.UTF_8))
        else JSONObject()

    private fun write(file: AtomicFile, value: JSONObject) {
        check(root.exists() || root.mkdirs()) { "Cannot create room media folder." }
        val stream = file.startWrite()
        try {
            stream.write(value.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    fun saveVisit(visit: CaptureVisit) = synchronized(lock) {
        val index = read(visits)
        if (!index.has(visit.id)) {
            index.put(visit.id, JSONObject().put("created", visit.created).nullable("propertyId", visit.propertyId))
            write(visits, index)
        }
    }

    fun visits(propertyId: String? = null): List<CaptureVisit> = synchronized(lock) {
        val index = read(visits)
        index.keys().asSequence().map { id ->
            val item = index.getJSONObject(id)
            CaptureVisit(id, item.string("propertyId"), item.getLong("created"))
        }.filter { propertyId == null || it.propertyId == propertyId }.sortedByDescending { it.created }.toList()
    }

    fun image(id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(root, "$id.jpg")
    }

    fun savePhoto(bitmap: Bitmap, propertyId: String?, visitId: String?, clipId: String? = null,
                  sourceMillis: Long? = null): RoomPhoto = synchronized(lock) {
        val photo = RoomPhoto(UUID.randomUUID().toString(), System.currentTimeMillis(), propertyId, visitId, clipId, sourceMillis, null)
        val index = read(photos)
        val items = index.optJSONObject("items") ?: JSONObject().also { index.put("items", it) }
        check(root.exists() || root.mkdirs()) { "Cannot create room media folder." }
        val destination = image(photo.id)
        try {
            destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            items.put(photo.id, JSONObject().put("created", photo.created).nullable("propertyId", propertyId)
                .nullable("visitId", visitId).nullable("clipId", clipId).nullable("sourceMillis", sourceMillis))
            write(photos, index)
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
        photo
    }

    fun all(propertyId: String? = null, visitId: String? = null, clipId: String? = null): List<RoomPhoto> = synchronized(lock) {
        val index = read(photos)
        val items = index.optJSONObject("items") ?: return@synchronized emptyList()
        val rooms = index.optJSONObject("rooms") ?: JSONObject()
        items.keys().asSequence().map { id ->
            val item = items.getJSONObject(id)
            RoomPhoto(id, item.getLong("created"), item.string("propertyId"), item.string("visitId"),
                item.string("clipId"), item.optLong("sourceMillis", -1).takeIf { it >= 0 },
                item.string("roomId")?.let { rooms.optJSONObject(it)?.room(it) })
        }.filter { (propertyId == null || it.propertyId == propertyId) && (visitId == null || it.visitId == visitId) &&
            (clipId == null || it.clipId == clipId) && image(it.id).isFile }.sortedByDescending { it.created }.toList()
    }

    fun rooms(scope: String): List<RoomDetails> = all().asSequence().filter { it.scope == scope }
        .mapNotNull { it.room }.distinctBy { it.id }.sortedBy { it.name.lowercase() }.toList()

    fun assign(photo: RoomPhoto, room: RoomDetails) = synchronized(lock) {
        require(room.scope == photo.scope && room.name.isNotBlank() && room.name.length <= 80)
        require((room.lengthMetres == null) == (room.widthMetres == null))
        require(listOfNotNull(room.lengthMetres, room.widthMetres, room.heightMetres).all { it.isFinite() && it > 0 })
        require(room.lengthMetres == null || (room.lengthMetres * room.widthMetres!!).isFinite())
        val index = read(photos)
        val item = index.getJSONObject("items").getJSONObject(photo.id)
        val rooms = index.optJSONObject("rooms") ?: JSONObject().also { index.put("rooms", it) }
        require(rooms.optJSONObject(room.id)?.getString("scope")?.let { it == photo.scope } ?: true)
        rooms.put(room.id, JSONObject().put("scope", room.scope).put("type", room.type).put("name", room.name)
            .nullable("length", room.lengthMetres).nullable("width", room.widthMetres).nullable("height", room.heightMetres)
            .put("feet", room.feet).put("notes", room.notes))
        item.put("roomId", room.id)
        write(photos, index)
    }

    fun delete(photo: RoomPhoto) = synchronized(lock) {
        val index = read(photos)
        index.getJSONObject("items").remove(photo.id)
        write(photos, index)
        image(photo.id).delete()
    }

    private fun JSONObject.room(id: String) = RoomDetails(id, getString("scope"), getString("type"), getString("name"),
        optDouble("length").takeIf { it.isFinite() }, optDouble("width").takeIf { it.isFinite() },
        optDouble("height").takeIf { it.isFinite() }, optBoolean("feet"), optString("notes"))

    private fun JSONObject.string(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONObject.nullable(key: String, value: Any?) = put(key, value ?: JSONObject.NULL)

    companion object { private val lock = Any() }
}

internal fun stampPhoto(bitmap: Bitmap, bearing: Double?): Bitmap {
    val output = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: error("Cannot render photo.")
    CompassPlacement.draw(Canvas(output), Rect(0, 0, output.width, output.height), 0, bearing)
    return output
}

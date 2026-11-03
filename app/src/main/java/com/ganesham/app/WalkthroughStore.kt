package com.ganesham.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class SavedWalkthrough(val id: String, val created: Long, val propertyId: String?, val durationMillis: Long)

internal class WalkthroughStore(context: Context) {
    private val directory = File(context.filesDir, "walkthroughs")
    fun prepareFile(): File {
        check(directory.exists() || directory.mkdirs()) { "Cannot create the walkthrough folder." }
        return File(directory, "${UUID.randomUUID()}.mp4")
    }
    fun movie(id: String): File {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) { "Invalid walkthrough identifier" }
        return File(directory, "$id.mp4")
    }
    fun complete(file: File, propertyId: String?, durationMillis: Long): SavedWalkthrough {
        check(file.parentFile?.canonicalFile == directory.canonicalFile && file.exists() && file.length() > 0 && durationMillis > 0) { "Recording is empty or incomplete." }
        val clip = SavedWalkthrough(file.nameWithoutExtension, System.currentTimeMillis(), propertyId, durationMillis)
        val json = JSONObject().put("id",clip.id).put("created",clip.created)
            .put("propertyId",propertyId ?: JSONObject.NULL).put("durationMillis",durationMillis)
        val metadata = AtomicFile(File(directory,"${clip.id}.json"))
        val stream = metadata.startWrite()
        try { stream.write(json.toString().toByteArray(Charsets.UTF_8)); metadata.finishWrite(stream) }
        catch(e: Exception) { metadata.failWrite(stream); throw e }
        return clip
    }
    fun all(propertyId: String? = null): List<SavedWalkthrough> = directory.listFiles().orEmpty()
        .filter { it.extension == "json" }.mapNotNull { file ->
            runCatching {
                val json=JSONObject(file.readText())
                val id=json.getString("id")
                val clip=SavedWalkthrough(id,json.getLong("created"),if(json.isNull("propertyId")) null else json.getString("propertyId"),json.getLong("durationMillis"))
                if(movie(id).isFile && movie(id).length() > 0 && (propertyId == null || propertyId == clip.propertyId)) clip else null
            }.getOrNull()
        }.sortedByDescending { it.created }
}

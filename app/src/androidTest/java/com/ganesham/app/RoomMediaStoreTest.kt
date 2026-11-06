package com.ganesham.app

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class RoomMediaStoreTest {
    @Test fun roomDetailsAreSharedWithinOnePropertyAndSurviveReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "room-test-${UUID.randomUUID()}")
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = directory
        }
        val bitmap = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888)
        try {
            val store = RoomMediaStore(isolated)
            val visit = CaptureVisit.create("property-a")
            store.saveVisit(visit)
            val first = store.savePhoto(bitmap, "property-a", visit.id)
            val second = store.savePhoto(bitmap, "property-a", visit.id, "video-a", 520)
            val unrelated = store.savePhoto(bitmap, "property-b", null)
            val room = RoomDetails(UUID.randomUUID().toString(), first.scope, "Bedroom", "Bedroom 1", 4.0, 3.0, null, false, "")
            store.assign(first, room)
            store.assign(second, room)
            store.assign(second, room.copy(widthMetres = 3.5, notes = "Measured inside wall"))
            val reopened = RoomMediaStore(isolated)
            val photos = reopened.all(visitId = visit.id)
            assertEquals(2, photos.size)
            assertTrue(photos.all { it.room?.widthMetres == 3.5 && it.room?.notes == "Measured inside wall" })
            assertEquals(520L, photos.first { it.id == second.id }.sourceMillis)
            assertNull(reopened.all("property-b").single().room)
            assertEquals(visit.id, reopened.visits("property-a").single().id)
            assertThrows(IllegalArgumentException::class.java) { reopened.assign(unrelated, room) }
            assertThrows(IllegalArgumentException::class.java) { reopened.assign(first, room.copy(widthMetres = null)) }
            reopened.delete(first)
            assertFalse(reopened.image(first.id).exists())
            assertEquals(room.id, reopened.all(visitId = visit.id).single().room?.id)
        } finally {
            bitmap.recycle()
            directory.deleteRecursively()
        }
    }
}

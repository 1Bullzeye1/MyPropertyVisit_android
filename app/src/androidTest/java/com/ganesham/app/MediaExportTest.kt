package com.ganesham.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class MediaExportTest {
    @Test fun batchPickerSelectsAllAndDisablesActionsForEmptySelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = RoomMediaStore(context)
        val property = "export-test-" + UUID.randomUUID()
        val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
        val photos = listOf(store.savePhoto(bitmap, property, null), store.savePhoto(bitmap, property, null))
        bitmap.recycle()
        val scenario = androidx.test.core.app.ActivityScenario.launch<MediaExportActivity>(
            android.content.Intent(context, MediaExportActivity::class.java).putExtra("propertyId", property).putExtra("photosOnly", true))
        fun descendants(view: android.view.View): List<android.view.View> = listOf(view) +
            if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        try {
            val deadline = System.currentTimeMillis() + 10000
            var ready = false
            while (!ready && System.currentTimeMillis() < deadline) {
                scenario.onActivity { ready = descendants(it.window.decorView).filterIsInstance<android.widget.CheckBox>().size == 2 }
                if (!ready) Thread.sleep(100)
            }
            assertTrue("Batch picker did not load both scoped photos", ready)
            scenario.onActivity { activity ->
                val views = descendants(activity.window.decorView)
                val checks = views.filterIsInstance<android.widget.CheckBox>()
                val buttons = views.filterIsInstance<android.widget.Button>()
                val toggle = buttons.first { it.text == "Deselect all" }
                val save = buttons.first { it.text == "Save selected to Gallery" }
                val share = buttons.first { it.text == "Share selected" }
                assertTrue(checks.all { it.isChecked }); assertTrue(save.isEnabled); assertTrue(share.isEnabled)
                toggle.performClick()
                assertTrue(checks.none { it.isChecked }); assertFalse(save.isEnabled); assertFalse(share.isEnabled)
                checks.first().performClick()
                assertTrue(save.isEnabled); assertTrue(share.isEnabled)
                toggle.performClick()
                assertTrue(checks.all { it.isChecked })
            }
        } finally { scenario.close(); photos.forEach { store.delete(it) } }
    }

    @Test fun savesStampedJpegWithoutChangingBytesAndPublishesGalleryEntry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val stamped = stampPhoto(source, 123.0)
        val store = RoomMediaStore(context)
        val photo = store.savePhoto(stamped, null, null, null, null)
        val file = store.image(photo.id)
        var uri: android.net.Uri? = null
        try {
            val shared = FileProvider.getUriForFile(context, "${context.packageName}.walkthroughs", file)
            context.contentResolver.openInputStream(shared)!!.use { assertArrayEquals(file.readBytes(), it.readBytes()) }
            uri = saveMediaToGallery(context, file, false)
            val resolver = context.contentResolver
            resolver.openInputStream(uri)!!.use { input ->
                val bytes = input.readBytes()
                assertArrayEquals(file.readBytes(), bytes)
                val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                assertNotNull(image); assertEquals(720, image.width); image.recycle()
            }
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_PENDING, MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)!!.use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)); assertEquals("Pictures/MyPropertyVisit/", it.getString(1))
            }
        } finally {
            uri?.let { context.contentResolver.delete(it, null, null) }
            store.delete(photo); stamped.recycle(); source.recycle()
        }
    }
    @Test fun refusesMissingMediaBeforeCreatingGalleryItem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try { saveMediaToGallery(context, File(context.cacheDir, UUID.randomUUID().toString()), false); fail("Missing file accepted") }
        catch (_: IllegalStateException) { }
    }
}

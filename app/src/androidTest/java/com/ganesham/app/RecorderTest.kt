package com.ganesham.app

import android.Manifest
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.VideoView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class RecorderTest {
    private fun button(view: View, title: String): Button? {
        if(view is Button && view.text.toString()==title) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) button(view.getChildAt(i),title)?.let { return it }
        return null
    }
    private fun video(view: View): VideoView? {
        if(view is VideoView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) video(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline=System.currentTimeMillis()+40000
        while(System.currentTimeMillis()<deadline) { if(condition()) return; Thread.sleep(300) }
        fail(message)
    }
    @Test fun savesVideoOnlyRecordingForOfflinePlayback() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.CAMERA)
        val store=WalkthroughStore(context)
        val roomMedia = RoomMediaStore(context)
        val property="recorder-test-${java.util.UUID.randomUUID()}"
        val scenario=ActivityScenario.launch<RecorderActivity>(Intent(context,RecorderActivity::class.java).putExtra("propertyId",property))
        try {
            waitUntil("Camera did not become ready") {
                var ready=false
                scenario.onActivity { activity -> ready=button(activity.window.decorView,"Record")?.isEnabled==true }
                ready
            }
            scenario.onActivity { button(it.window.decorView,"Record")!!.performClick() }
            waitUntil("Recording did not start") {
                var started=false
                scenario.onActivity { activity -> started=button(activity.window.decorView,"Stop & save")?.isEnabled==true }
                started
            }
            Thread.sleep(1200)
            scenario.onActivity { button(it.window.decorView, "Pause")!!.performClick() }
            waitUntil("Recording did not pause") {
                var paused = false
                scenario.onActivity { paused = button(it.window.decorView, "Resume")?.isEnabled == true }
                paused
            }
            scenario.onActivity { button(it.window.decorView, "Photo")!!.performClick() }
            waitUntil("Photo taken during pause was not saved") { roomMedia.all(property).size == 1 }
            val photo = roomMedia.all(property).single()
            val image = android.graphics.BitmapFactory.decodeFile(roomMedia.image(photo.id).absolutePath)
            assertNotNull("Saved photo is not a decodable JPEG", image)
            val gold = (0 until (image.height * .3).toInt()).sumOf { y ->
                (0 until (image.width * .3).toInt()).count { x ->
                    val pixel = image.getPixel(x, y)
                    android.graphics.Color.red(pixel) > 190 && android.graphics.Color.green(pixel) in 140..230 && android.graphics.Color.blue(pixel) < 150
                }
            }
            image.recycle()
            assertTrue("Saved photo is missing the compass marker", gold > 2)
            waitUntil("Resume was not enabled after photo save") {
                var ready = false
                scenario.onActivity { ready = button(it.window.decorView, "Resume")?.isEnabled == true }
                ready
            }
            scenario.onActivity { button(it.window.decorView, "Resume")!!.performClick() }
            waitUntil("Recording did not resume") {
                var resumed = false
                scenario.onActivity { resumed = button(it.window.decorView, "Pause")?.isEnabled == true }
                resumed
            }
            Thread.sleep(1200)
            scenario.onActivity { button(it.window.decorView,"Stop & save")!!.performClick() }
            waitUntil("Movie and metadata were not saved") { store.all(property).isNotEmpty() }
            val clip=store.all(property).single()
            assertTrue(clip.durationMillis>0)
            assertNotNull(clip.visitId)
            assertEquals(clip.visitId, photo.visitId)
            assertEquals(clip.visitId, roomMedia.visits(property).single().id)
            assertTrue("Compass overlay metadata was not set",clip.compassEmbedded)
            val media=MediaMetadataRetriever()
            try {
                media.setDataSource(store.movie(clip.id).absolutePath)
                assertTrue(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()>0)
                assertTrue(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()>0)
                assertNotEquals("yes",media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                val frame=media.getFrameAtTime(0)
                assertNotNull("Recorded file has no decodable frame",frame)
                if(frame!=null) {
                    var goldPixels=0
                    var markerPixels=0
                    for(y in 0 until (frame.height*0.3).toInt()) for(x in 0 until (frame.width*0.3).toInt()) {
                        val pixel=frame.getPixel(x,y)
                        if(android.graphics.Color.red(pixel)>190 && android.graphics.Color.green(pixel) in 140..230 && android.graphics.Color.blue(pixel)<150) {
                            goldPixels++
                            if(x>frame.width*0.10 && x<frame.width*0.16 && y>frame.width*0.025 && y<frame.width*0.085) markerPixels++
                        }
                    }
                    assertTrue("Compass top marker is missing from encoded top-left frame",goldPixels>2)
                    assertTrue("Compass is shifted or clipped in the final recorded viewport",markerPixels>2)
                    frame.recycle()
                }
            } finally { media.release() }
            // Decode alone did not detect the former opaque VideoView background.
            // Wait for a rendered frame, then inspect the actual on-screen surface.
            scenario.close()
            val library=ActivityScenario.launch<WalkthroughLibraryActivity>(Intent(context,WalkthroughLibraryActivity::class.java).putExtra("clipId",clip.id))
            try {
                var bounds=intArrayOf(0,0,0,0)
                waitUntil("Saved video did not render on screen") {
                    var rendered=false
                    library.onActivity { activity ->
                        val view=video(activity.window.decorView)
                        val loading=activity.window.decorView.findViewWithTag<View>("videoLoading")
                        if(view!=null && view.width>0 && view.height>0 && loading?.visibility==View.GONE) {
                            assertNull("VideoView must not have an opaque background",view.background)
                            val location=IntArray(2);view.getLocationOnScreen(location)
                            bounds=intArrayOf(location[0],location[1],view.width,view.height)
                            rendered=true
                        }
                    }
                    rendered
                }
                // Camera content may be genuinely dark (e.g. a phone resting on
                // a desk). The embedded gold marker is a known visible feature.
                // Also allow queued frames to reach the compositor after the
                // first-frame callback instead of assuming immediate display.
                waitUntil("Recorded compass marker is not visible on the playback surface") {
                    val screen=instrumentation.uiAutomation.takeScreenshot()
                    var markerPixels=0
                    if(screen!=null) {
                        val left=bounds[0];val top=bounds[1];val width=bounds[2]
                        for(y in top+(width*0.025).toInt() until top+(width*0.085).toInt()) {
                            for(x in left+(width*0.10).toInt() until left+(width*0.16).toInt()) {
                                if(x in 0 until screen.width && y in 0 until screen.height) {
                                    val c=screen.getPixel(x,y)
                                    if(android.graphics.Color.red(c)>190 && android.graphics.Color.green(c) in 140..230 && android.graphics.Color.blue(c)<150) markerPixels++
                                }
                            }
                        }
                        screen.recycle()
                    }
                    markerPixels>2
                }
            } finally { library.close() }

            val gallery = ActivityScenario.launch<RoomPhotosActivity>(
                Intent(context, RoomPhotosActivity::class.java).putExtra("visitId", clip.visitId))
            try {
                fun preview(view: View): View? {
                    if (view.contentDescription == "View ${photo.title} full screen") return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) preview(view.getChildAt(i))?.let { return it }
                    return null
                }
                waitUntil("Photo did not appear in its visit gallery") {
                    var found = false
                    gallery.onActivity { found = preview(it.window.decorView) != null }
                    found
                }
                gallery.onActivity { preview(it.window.decorView)!!.performClick() }
                waitUntil("Full-screen photo viewer did not open") {
                    val root = instrumentation.uiAutomation.rootInActiveWindow
                    root?.findAccessibilityNodeInfosByText("×")?.any { it.contentDescription == "Close photo" } == true
                }
            } finally { gallery.close() }
        } finally {
            scenario.close()
            roomMedia.all(property).forEach { roomMedia.delete(it) }
            store.all(property).forEach { clip ->
                store.movie(clip.id).delete()
                java.io.File(context.filesDir,"walkthroughs/${clip.id}.json").delete()
            }
        }
    }
}

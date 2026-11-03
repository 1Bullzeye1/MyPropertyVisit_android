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
            Thread.sleep(2500)
            scenario.onActivity { button(it.window.decorView,"Stop & save")!!.performClick() }
            waitUntil("Movie and metadata were not saved") { store.all(property).isNotEmpty() }
            val clip=store.all(property).single()
            assertTrue(clip.durationMillis>0)
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
                    for(y in 0 until (frame.height*0.3).toInt()) for(x in 0 until (frame.width*0.3).toInt()) {
                        val pixel=frame.getPixel(x,y)
                        if(android.graphics.Color.red(pixel)>190 && android.graphics.Color.green(pixel) in 140..230 && android.graphics.Color.blue(pixel)<150) goldPixels++
                    }
                    assertTrue("Compass top marker is missing from encoded top-left frame",goldPixels>2)
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
                val screen=instrumentation.uiAutomation.takeScreenshot()
                assertNotNull(screen)
                if(screen!=null) {
                    var visible=0
                    for(y in bounds[1]+bounds[3]/4 until bounds[1]+bounds[3]*3/4 step 8) {
                        for(x in bounds[0]+bounds[2]/4 until bounds[0]+bounds[2]*3/4 step 8) {
                            if(x in 0 until screen.width && y in 0 until screen.height) {
                                val c=screen.getPixel(x,y)
                                if(android.graphics.Color.red(c)+android.graphics.Color.green(c)+android.graphics.Color.blue(c)>50) visible++
                            }
                        }
                    }
                    assertTrue("Video surface is black despite successful decoding",visible>10)
                    screen.recycle()
                }
            } finally { library.close() }
        } finally {
            scenario.close()
            store.all(property).forEach { clip ->
                store.movie(clip.id).delete()
                java.io.File(context.filesDir,"walkthroughs/${clip.id}.json").delete()
            }
        }
    }
}

package com.ganesham.app

import android.Manifest
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
            val media=MediaMetadataRetriever()
            try {
                media.setDataSource(store.movie(clip.id).absolutePath)
                assertTrue(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()>0)
                assertTrue(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()>0)
                assertNotEquals("yes",media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                val frame=media.getFrameAtTime(0)
                assertNotNull("Recorded file has no decodable frame",frame)
                frame?.recycle()
            } finally { media.release() }
        } finally {
            scenario.close()
            store.all(property).forEach { clip ->
                store.movie(clip.id).delete()
                java.io.File(context.filesDir,"walkthroughs/${clip.id}.json").delete()
            }
        }
    }
}

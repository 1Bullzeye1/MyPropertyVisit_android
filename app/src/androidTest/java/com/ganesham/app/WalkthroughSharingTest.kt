package com.ganesham.app

import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WalkthroughSharingTest {
    @Test fun sharesOneReadableMp4WithTemporaryReadPermission() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=WalkthroughStore(context).prepareFile()
        val bytes=byteArrayOf(0,0,0,16,102,116,121,112,109,112,52,50)
        try {
            file.writeBytes(bytes)
            val intent=WalkthroughSharing.intent(context,file.nameWithoutExtension)
            val uri=intent.clipData!!.getItemAt(0).uri
            assertEquals(Intent.ACTION_SEND,intent.action)
            assertEquals("video/mp4",intent.type)
            assertEquals("content",uri.scheme)
            assertTrue(intent.hasExtra(Intent.EXTRA_STREAM))
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0,intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals(1,intent.clipData!!.itemCount)
            assertEquals("video/mp4",context.contentResolver.getType(uri))
            context.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(bytes,it.readBytes()) }
            val provider=context.packageManager.resolveContentProvider("${context.packageName}.walkthroughs",0)!!
            assertFalse(provider.exported)
            assertTrue(provider.grantUriPermissions)
        } finally { file.delete() }
    }
    @Test fun providerDoesNotExposeSettingsOrOtherPrivateFiles() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.filesDir,"private-sharing-test-${java.util.UUID.randomUUID()}.txt")
        try {
            file.writeText("not a walkthrough")
            try { FileProvider.getUriForFile(context,"${context.packageName}.walkthroughs",file);fail("Private file should not be shareable") }
            catch(_:IllegalArgumentException) { }
        } finally { file.delete() }
    }
}

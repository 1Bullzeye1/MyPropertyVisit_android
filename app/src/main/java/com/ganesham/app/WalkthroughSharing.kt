package com.ganesham.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider

internal object WalkthroughSharing {
    fun intent(context:Context,id:String):Intent {
        val file=WalkthroughStore(context).movie(id)
        check(file.isFile && file.length()>0) { "This walkthrough is no longer available." }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.walkthroughs",file)
        return Intent(Intent.ACTION_SEND).apply {
            type="video/mp4"
            putExtra(Intent.EXTRA_STREAM,uri)
            putExtra(Intent.EXTRA_TITLE,"MyPropertyVisit walkthrough")
            clipData=ClipData.newUri(context.contentResolver,"MyPropertyVisit walkthrough",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

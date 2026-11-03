package com.ganesham.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class CompassTest {
    @Test fun cameraBearingUsesRearCameraAxisAndRejectsVerticalViews() {
        fun matrix(east:Float,north:Float)=FloatArray(9).apply { this[2]=-east;this[5]=-north }
        assertEquals(0.0,MagneticCompass.cameraBearing(matrix(0f,1f))!!,0.01)
        assertEquals(90.0,MagneticCompass.cameraBearing(matrix(1f,0f))!!,0.01)
        assertEquals(180.0,MagneticCompass.cameraBearing(matrix(0f,-1f))!!,0.01)
        assertEquals(270.0,MagneticCompass.cameraBearing(matrix(-1f,0f))!!,0.01)
        assertNull(MagneticCompass.cameraBearing(matrix(0f,0f)))
    }
    @Test fun dialShowsNorthNeedleAndUnavailableState() {
        fun image(bearing:Double?)=Bitmap.createBitmap(160,196,Bitmap.Config.ARGB_8888).also { CompassArtwork.draw(Canvas(it),bearing) }
        val north=image(0.0);val east=image(90.0);val unavailable=image(null)
        try {
            assertTrue(Color.red(north.getPixel(80,55))>Color.green(north.getPixel(80,55))*2)
            assertTrue(Color.red(east.getPixel(55,80))>Color.green(east.getPixel(55,80))*2)
            assertEquals(Color.red(unavailable.getPixel(80,80)),Color.green(unavailable.getPixel(80,80)))
        } finally { north.recycle();east.recycle();unavailable.recycle() }
    }
    @Test fun wholeDialStaysInsideCroppedAndRotatedVideo() {
        for(rotation in listOf(0,90,180,270)) {
            val portrait=rotation%180==0
            val buffer=Bitmap.createBitmap(if(portrait) 1440 else 1920,if(portrait) 1920 else 1440,Bitmap.Config.ARGB_8888)
            val crop=if(portrait) android.graphics.Rect(180,0,1260,1920) else android.graphics.Rect(0,180,1920,1260)
            CompassPlacement.draw(Canvas(buffer),crop,rotation,0.0)
            val matrix=android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
            val output=Bitmap.createBitmap(buffer,crop.left,crop.top,crop.width(),crop.height(),matrix,true)
            try {
                var left=output.width;var top=output.height;var right=0;var bottom=0
                for(y in 0 until output.height step 2) for(x in 0 until output.width step 2) {
                    if(Color.alpha(output.getPixel(x,y))>0) { left=minOf(left,x);top=minOf(top,y);right=maxOf(right,x);bottom=maxOf(bottom,y) }
                }
                assertTrue("Dial clipped at left for $rotation degrees",left>output.width*0.03)
                assertTrue("Dial clipped at top for $rotation degrees",top>output.width*0.03)
                assertTrue("Dial width changed for $rotation degrees",right<output.width*0.24)
                assertTrue("Readout clipped for $rotation degrees",bottom<output.height && bottom>top+output.width*0.18)
            } finally { output.recycle();buffer.recycle() }
        }
    }

}

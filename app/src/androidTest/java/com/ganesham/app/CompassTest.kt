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
}

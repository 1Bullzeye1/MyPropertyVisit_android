package com.ganesham.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.*

internal data class MagneticReading(val degrees: Double, val timestampNanos: Long)

// Sensor callbacks run on the main looper; immutable snapshots are read by the
// overlay rendering thread through one volatile reference.
internal class MagneticCompass(context: Context):SensorEventListener {
    private val manager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetic=manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotation=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gravity=manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var gravityValues:FloatArray?=null
    private var magneticValues:FloatArray?=null
    @Volatile private var reading:MagneticReading?=null
    fun start() {
        reading=null
        if(magnetic==null) return
        if(rotation!=null) manager.registerListener(this,rotation,SensorManager.SENSOR_DELAY_UI)
        else if(gravity!=null) {
            manager.registerListener(this,gravity,SensorManager.SENSOR_DELAY_UI)
            manager.registerListener(this,magnetic,SensorManager.SENSOR_DELAY_UI)
        }
    }
    fun stop() { manager.unregisterListener(this); reading=null;gravityValues=null;magneticValues=null }
    fun current():Double? = reading?.takeIf { SystemClock.elapsedRealtimeNanos()-it.timestampNanos in 0..3_000_000_000L }?.degrees
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) { if(accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE) reading=null }
    override fun onSensorChanged(event:SensorEvent) {
        if(event.accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE) { reading=null;return }
        val matrix=FloatArray(9)
        if(event.sensor.type==Sensor.TYPE_ROTATION_VECTOR) SensorManager.getRotationMatrixFromVector(matrix,event.values)
        else {
            if(event.sensor.type==Sensor.TYPE_MAGNETIC_FIELD) magneticValues=event.values.clone() else gravityValues=event.values.clone()
            val g=gravityValues ?: return;val m=magneticValues ?: return
            if(!SensorManager.getRotationMatrix(matrix,null,g,m)) { reading=null;return }
        }
        reading=cameraBearing(matrix)?.let { MagneticReading(it,event.timestamp) }
    }
    companion object {
        // Android's +Z points out of the screen. The rear camera looks along -Z.
        // Project that axis onto the east/north plane. Looking straight up/down
        // has no stable azimuth and must not produce a fictitious direction.
        fun cameraBearing(matrix:FloatArray):Double? {
            val east=-matrix[2].toDouble();val north=-matrix[5].toDouble()
            if(hypot(east,north)<0.15) return null
            return (Math.toDegrees(atan2(east,north))+360)%360
        }
    }
}

internal object CompassArtwork {
    fun draw(canvas:Canvas,bearing:Double?) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val save=canvas.save()
        paint.color=Color.argb(220,10,14,20)
        canvas.drawCircle(80f,80f,77f,paint)
        canvas.drawRoundRect(24f,162f,136f,194f,12f,12f,paint)
        canvas.save();canvas.rotate(-(bearing ?: 0.0).toFloat(),80f,80f)
        for(tick in 0..23) {
            canvas.save();canvas.rotate(tick*15f,80f,80f)
            paint.color=if(tick%6==0) Color.WHITE else Color.GRAY
            paint.strokeWidth=if(tick%6==0) 3f else 1.5f
            canvas.drawLine(80f,15f,80f,if(tick%6==0) 26f else 21f,paint)
            canvas.restore()
        }
        canvas.restore()
        if(bearing!=null) {
            listOf("N","E","S","W").forEachIndexed { index,label ->
                val a=index*PI/2-PI/2-Math.toRadians(bearing)
                paint.color=if(index==0) Color.rgb(255,102,110) else Color.WHITE
                paint.textSize=19f;paint.typeface=Typeface.DEFAULT_BOLD;paint.textAlign=Paint.Align.CENTER
                val y=80+sin(a)*48
                canvas.drawText(label,(80+cos(a)*48).toFloat(),(y-(paint.ascent()+paint.descent())/2).toFloat(),paint)
            }
            canvas.save();canvas.rotate(-bearing.toFloat(),80f,80f)
            fun needle(tip:Float,color:Int) {
                paint.color=color
                val path=Path().apply { moveTo(80f,tip);lineTo(70f,80f);lineTo(90f,80f);close() }
                canvas.drawPath(path,paint)
            }
            needle(47f,Color.rgb(255,77,87));needle(113f,Color.WHITE)
            canvas.restore()
        } else {
            paint.color=Color.LTGRAY;paint.strokeWidth=4f
            canvas.drawLine(57f,103f,103f,57f,paint)
        }
        paint.color=Color.rgb(255,200,102);canvas.drawCircle(80f,11f,4f,paint)
        paint.color=Color.WHITE;paint.textSize=25f;paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("monospace",Typeface.BOLD)
        canvas.drawText(bearing?.let { "${it.roundToInt()%360}°" } ?: "—",80f,178f-(paint.ascent()+paint.descent())/2,paint)
        canvas.restoreToCount(save)
    }
}

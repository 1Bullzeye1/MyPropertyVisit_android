package com.ganesham.app

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.*
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.content.res.AppCompatResources

internal class LensUi(val context:Context) {
    fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
    private val dark get()=context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
    private fun color(day:String,night:String)=Color.parseColor(if(dark) night else day)
    val background get()=color("#F6F5FB","#120F20")
    val surface get()=color("#FFFFFF","#201A32")
    val ink get()=color("#25213D","#F1ECFF")
    val muted get()=color("#69647C","#C9BED9")
    val accent get()=color("#6250C8","#C6B7FA")
    val action get()=color("#6250C8","#5945A4")
    val line get()=color("#E8E4F3","#423453")
    fun shape(fill:Int,radius:Int=18)=GradientDrawable().apply { setColor(fill);cornerRadius=dp(radius).toFloat() }
    fun text(value:String,size:Float=16f,bold:Boolean=false,color:Int=ink)=TextView(context).apply {
        text=value;textSize=size;setTextColor(color)
        if(bold) setTypeface(typeface,Typeface.BOLD)
    }
    fun icon(resource:Int,tint:Int=accent)=ImageView(context).apply { setImageResource(resource);imageTintList=ColorStateList.valueOf(tint) }
    fun iconButton(resource:Int,label:String,tint:Int=accent,onClick:()->Unit)=ImageButton(context).apply {
        setImageDrawable(AppCompatResources.getDrawable(context,resource));imageTintList=ColorStateList.valueOf(tint)
        background=RippleDrawable(ColorStateList.valueOf(0x226250C8),shape(Color.TRANSPARENT,24),shape(Color.WHITE,24))
        contentDescription=label;setPadding(dp(12),dp(12),dp(12),dp(12));setOnClickListener { onClick() }
    }
    fun hero(title: String, subtitle: String) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(22), dp(22), dp(22))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(color("#282148", "#282044"), color("#514184", "#493675"))).apply {
            cornerRadius = dp(24).toFloat()
        }
        addView(text(title, 28f, false, Color.WHITE).apply { typeface = Typeface.SERIF })
        addView(text(subtitle, 14f, false, 0xFFEAE4FB.toInt()).apply { setPadding(0, dp(8), 0, 0) })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }

    fun menuCard(title: String, subtitle: String, resource: Int, action: (() -> Unit)? = null) =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = shape(surface, 20).apply { setStroke(dp(1), line) }
            addView(icon(resource).apply {
                background = shape(color("#EEEAFB", "#332747"), 13)
                setPadding(dp(11), dp(11), dp(11), dp(11))
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                addView(text(title, 16f, true))
                addView(text(subtitle, 13f, false, muted))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (action != null) {
                addView(text("↗", 18f, false, accent))
                isFocusable = true
                setOnClickListener { action() }
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }

    fun action(label:String,resource:Int,onClick:()->Unit)=Button(context).apply {
        text=label;isAllCaps=false;textSize=16f;setTypeface(typeface,Typeface.BOLD)
        setTextColor(Color.WHITE);background=RippleDrawable(ColorStateList.valueOf(0x22FFFFFF),shape(action,18),shape(Color.WHITE,18))
        setPadding(dp(20),dp(8),dp(20),dp(8));minHeight=dp(56)
        val drawable=AppCompatResources.getDrawable(context,resource)?.mutate()?.apply { setTint(Color.WHITE);setBounds(0,0,dp(22),dp(22)) }
        setCompoundDrawablesRelative(drawable,null,null,null);compoundDrawablePadding=dp(10)
        setOnClickListener { onClick() }
    }
}

// Keep the button's text for accessibility and state tests, while drawing a
// familiar record circle / stop square instead of a platform rectangle.
internal class CaptureButton(context:Context):AppCompatButton(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { background=null;setTextColor(Color.TRANSPARENT);isAllCaps=false;setPadding(0,0,0,0) }
    override fun onDraw(canvas:Canvas) {
        val cx=width/2f;val cy=height/2f;val radius=minOf(width,height)*0.44f
        paint.color=Color.WHITE;paint.alpha=if(isEnabled) 255 else 125;paint.style=Paint.Style.STROKE;paint.strokeWidth=resources.displayMetrics.density*2.5f
        canvas.drawCircle(cx,cy,radius,paint)
        paint.style=Paint.Style.FILL;paint.color=Color.rgb(245,73,85);paint.alpha=if(isEnabled) 255 else 125
        if(text.toString().startsWith("Stop")) {
            val r=radius*0.42f
            canvas.drawRoundRect(cx-r,cy-r,cx+r,cy+r,r*0.22f,r*0.22f,paint)
        } else if(text.toString().contains("Saving")) {
            paint.color=Color.WHITE;paint.alpha=125;paint.style=Paint.Style.STROKE
            canvas.drawArc(cx-radius*0.5f,cy-radius*0.5f,cx+radius*0.5f,cy+radius*0.5f,-90f,270f,false,paint)
        } else canvas.drawCircle(cx,cy,radius*0.80f,paint)
    }
}

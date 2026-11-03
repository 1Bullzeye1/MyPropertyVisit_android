package com.ganesham.app

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.OnBackPressedCallback
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import java.util.UUID
import java.util.concurrent.Executors

internal data class Property(val id: String, val name: String, val type: String, val location: String)

internal class PropertyStore(context: Context, databaseName: String = "propertylens.db") : SQLiteOpenHelper(context, databaseName, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE properties (id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, location TEXT NOT NULL, created INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        error("No database migration exists from $old to $new")
    }
    fun all(): List<Property> = readableDatabase.rawQuery("SELECT id,name,type,location FROM properties ORDER BY created DESC", null).use { c ->
        buildList { while(c.moveToNext()) add(Property(c.getString(0), c.getString(1), c.getString(2), c.getString(3))) }
    }
    fun save(p: Property, editing: Boolean) {
        val values = ContentValues().apply { put("id", p.id); put("name", p.name); put("type", p.type); put("location", p.location) }
        if (editing) check(writableDatabase.update("properties", values, "id=?", arrayOf(p.id)) == 1)
        else { values.put("created", System.currentTimeMillis()); writableDatabase.insertOrThrow("properties", null, values) }
    }
    fun delete(id: String) { check(writableDatabase.delete("properties", "id=?", arrayOf(id)) == 1) }
}

class MainActivity : AppCompatActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var store: PropertyStore
    private lateinit var body: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var locationInput: EditText
    private lateinit var typeInput: Spinner
    private var records = emptyList<Property>()
    private var route = "home"
    private var selected: String? = null
    private var working = false
    private var draftName = ""
    private var draftLocation = ""
    private var draftType = "Flat"
    private val types = listOf("Land", "Flat", "Building")
    private val dark get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private fun color(light: String, night: String) = Color.parseColor(if(dark) night else light)
    private val background get() = color("#F6F5FB", "#111820")
    private val ink get() = color("#25213D", "#E9EFF5")
    private val secondary get() = color("#69647C", "#BAC5CF")
    private val accent get() = color("#6250C8", "#94C7ED")
    private val surface get() = color("#FFFFFF", "#1B2632")
    private val primary get() = color("#6250C8", "#295C7C")
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private val preferences get() = getSharedPreferences("propertylens.settings", Context.MODE_PRIVATE)
    private fun applyTheme(mode: String) {
        AppCompatDelegate.setDefaultNightMode(when(mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
    }
    override fun onCreate(state: Bundle?) {
        applyTheme(preferences.getString("theme", "system") ?: "system")
        super.onCreate(state)
        store = PropertyStore(applicationContext)
        route = (state?.getString("route") ?: "home").let { if(it=="record") "home" else it }
        selected = state?.getString("selected")
        draftName = state?.getString("name") ?: ""
        draftLocation = state?.getString("location") ?: ""
        draftType = state?.getString("type") ?: "Flat"
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (route == "home") finish() else back() }
        })
        render()
        window.decorView.post {
            window.insetsController?.setSystemBarsAppearance(if(dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        }
        load()
    }
    private fun load() {
        worker.execute {
            try { val loaded = store.all(); runOnUiThread { if(!isDestroyed) { records = loaded; if(route != "form") render() } } }
            catch(e: Exception) { runOnUiThread { if(!isDestroyed) showError("Could not load properties. Please reopen the app.") } }
        }
    }
    private fun text(value: String, size: Float = 16f, bold: Boolean = false, tint: Int = ink): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(tint)
        if(bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun rounded(fill: Int, radius: Float = 20f) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius.toInt()).toFloat() }
    private fun add(view: View, gap: Int = 14) { body.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(gap) }) }
    private fun card(title: String, subtitle: String, symbol: String, filled: Boolean = false, action: () -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(20), dp(18), dp(20)) }
        val shape = rounded(if(filled) primary else surface)
        row.background = RippleDrawable(ColorStateList.valueOf(color("#226250C8", "#2294C7ED")), shape, rounded(Color.WHITE))
        row.isClickable = true; row.isFocusable = true
        row.setOnClickListener { if(!working) action() }
        row.addView(text(symbol, 25f, false, if(filled) Color.WHITE else accent), LinearLayout.LayoutParams(dp(40), -2))
        val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8),0,dp(8),0) }
        words.addView(text(title, 17f, true, if(filled) Color.WHITE else ink))
        words.addView(text(subtitle, 13f, false, if(filled) Color.parseColor("#E4E7F4") else secondary).apply { setPadding(0,dp(5),0,0) })
        row.addView(words, LinearLayout.LayoutParams(0,-2,1f))
        row.addView(text("↗", 22f, false, if(filled) Color.WHITE else ink))
        add(row)
    }
    private fun go(next: String) { if(!working) { route = next; render() } }
    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(this@MainActivity.background) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,keyboard.bottom)); insets
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22),dp(24),dp(22),dp(24)) }
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1,-1)); setContentView(root); root.requestApplyInsets()
        if(route != "home") add(text("‹  Back", 16f, true, accent).apply { minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL; setOnClickListener { back() } })
        when(route) {
            "home" -> home()
            "settings" -> settings()
            "saved" -> saved()
            "form" -> form()
            "details" -> details()
            "join" -> { add(text("Join a property call",26f,true)); add(EditText(this).apply { hint = "Invitation code"; setSingleLine(); setTextColor(ink) }); add(text("Call joining is coming soon",18f,true)); add(text("Live calls are not connected in this Android build. Invitation codes cannot be used yet.",16f,false,secondary)) }
        }
    }
    private fun home() {
        selected = null
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("⌖  PropertyLens",24f,true,accent), LinearLayout.LayoutParams(0,-2,1f))
        header.addView(text("⚙",26f,false,accent).apply {
            gravity=Gravity.CENTER; contentDescription="Settings"; isFocusable=true
            setOnClickListener { go("settings") }
        }, LinearLayout.LayoutParams(dp(48),dp(48)))
        add(header,24)
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24),dp(26),dp(24),dp(26))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color("#282148","#233A4B"),color("#514184","#31566A"))).apply { cornerRadius = dp(26).toFloat() }
        }
        hero.addView(text("A CLEARER VIEW",12f,true,Color.parseColor("#DAD4F4")).apply { letterSpacing = 0.18f })
        hero.addView(text("Every angle.\nEvery detail.",36f,false,Color.WHITE).apply { typeface = Typeface.create("serif",Typeface.NORMAL); setPadding(0,dp(20),0,dp(18)) })
        hero.addView(text("Explore land, flats and buildings with the details that matter.",17f,false,Color.parseColor("#D9DFE8")))
        hero.addView(text("Live tours  ·  Directions  ·  Measurements",12f,false,Color.WHITE).apply { setPadding(0,dp(24),0,0) })
        add(hero,24)
        card("Record walkthrough", "Record video and save it offline", "◉") { startActivity(android.content.Intent(this,RecorderActivity::class.java).putExtra("propertyId",selected)) }
        add(text("Take a closer look",21f,true),18)
        card("Start a property call", "Prepare your property for a tour", "+",true) { selected = null; draftName=""; draftLocation=""; draftType="Flat"; go("form") }
        card("Join a call", "Enter an invitation code", "↗") { go("join") }
        card("Saved properties", "${records.size} saved on this device", "▤") { go("saved") }
        add(text("Built for a better-informed property visit.",12f,false,secondary).apply { gravity=Gravity.CENTER },0)
    }
    private fun saved() {
        add(text("Saved properties",27f,true))
        if(records.isEmpty()) add(text("No saved properties yet. Prepare a property from Home to save its details here.",16f,false,secondary))
        records.forEach { p -> card(p.name,"${p.type} · ${p.location}","⌂") { selected=p.id; go("details") } }
    }
    private fun form() {
        add(text(if(selected == null) "Prepare a property" else "Edit property",27f,true))
        add(text("Property name",14f,true))
        nameInput = EditText(this).apply { id=R.id.property_name; hint="e.g. Green Valley Flat"; setText(draftName); setSingleLine(); setTextColor(ink); setHintTextColor(secondary) }; add(nameInput)
        add(text("Property type",14f,true))
        typeInput=Spinner(this).apply { id=R.id.property_type; adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,types); setSelection(types.indexOf(draftType).coerceAtLeast(0)) }; add(typeInput)
        add(text("Area or address",14f,true))
        locationInput=EditText(this).apply { id=R.id.property_location; hint="Area, city or full address"; setText(draftLocation); setTextColor(ink); setHintTextColor(secondary); minLines=2 }; add(locationInput)
        add(text("Details are saved only on this device. Live calls are not connected yet.",14f,false,secondary))
        val button = Button(this).apply { setText(R.string.save_property); isAllCaps=false; setTextColor(Color.WHITE); background=rounded(primary,14f); minHeight=dp(52) }
        button.setOnClickListener {
            rememberDraft()
            if(draftName.isBlank()) { nameInput.error="Enter a property name"; return@setOnClickListener }
            if(draftLocation.isBlank()) { locationInput.error="Enter an area or address"; return@setOnClickListener }
            val editing=selected!=null
            val property=Property(selected ?: UUID.randomUUID().toString(),draftName,draftType,draftLocation)
            working=true; button.isEnabled=false
            worker.execute {
                try { store.save(property,editing); val loaded=store.all(); runOnUiThread { if(!isDestroyed) { working=false; records=loaded; selected=property.id; go("details") } } }
                catch(e: Exception) { runOnUiThread { if(!isDestroyed) { working=false; button.isEnabled=true; showError("Property could not be saved. Please try again.") } } }
            }
        }; add(button)
    }
    private fun details() {
        val p=records.firstOrNull { it.id==selected }
        if(p==null) { add(text("Property unavailable",25f,true)); return }
        add(text(p.name,27f,true)); add(text("${p.type}\n\n${p.location}",18f))
        card("Edit property","Update name, type or address","✎") { draftName=p.name; draftType=p.type; draftLocation=p.location; go("form") }
        card("Record walkthrough","Record video and save it offline","◉") { startActivity(android.content.Intent(this,RecorderActivity::class.java).putExtra("propertyId",selected)) }
        add(text("Measurements and visit history will appear here in a later milestone.",14f,false,secondary))
        add(Button(this).apply {
            setText(R.string.delete_property); isAllCaps=false
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Delete property?").setMessage("Delete ${p.name} from this device? This cannot be undone.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ ->
                        working=true
                        worker.execute {
                            try { store.delete(p.id); val loaded=store.all(); runOnUiThread { if(!isDestroyed) { working=false; records=loaded; selected=null; go("saved") } } }
                            catch(e: Exception) { runOnUiThread { if(!isDestroyed) { working=false; showError("Property could not be deleted.") } } }
                        }
                    }.show()
            }
        })
    }
    private fun settings() {
        add(text("Settings",27f,true))
        add(text("Account",19f,true))
        val name = preferences.getString("profile.name", "").orEmpty()
        card("Manage local profile", name.ifBlank { "Add your name and role" }, "♙") { editProfile() }
        add(text("Not signed in",16f,true))
        add(text("Sign-in, account switching and cloud sync are not connected yet. Your local profile stays on this device and is not a verified account.",14f,false,secondary),24)
        add(text("Display theme",19f,true))
        val choices = listOf("system", "light", "dark")
        val group = RadioGroup(this)
        val current = preferences.getString("theme", "system") ?: "system"
        choices.forEach { choice ->
            group.addView(RadioButton(this).apply {
                id=View.generateViewId()
                text=if(choice=="system") "System" else choice.replaceFirstChar { it.uppercase() }
                setTextColor(ink); minHeight=dp(52); tag=choice
                buttonTintList=ColorStateList.valueOf(accent)
                isChecked=choice==current
            })
        }
        group.setOnCheckedChangeListener { _, checked ->
            val choice=group.findViewById<RadioButton>(checked)?.tag as? String ?: return@setOnCheckedChangeListener
            preferences.edit().putString("theme",choice).apply()
            applyTheme(choice)
        }
        add(group)
        add(text("System follows your device appearance. Your choice is remembered for this app.",14f,false,secondary))
    }
    private fun editProfile() {
        val fields=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(12),dp(24),0) }
        val name=EditText(this).apply { hint="Display name (optional)"; setSingleLine(); setText(preferences.getString("profile.name","")) }
        val email=EditText(this).apply { hint="Email (optional, not verified)"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; setText(preferences.getString("profile.email","")) }
        val roles=listOf("Buyer","Seller","Broker")
        val role=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,roles); setSelection(roles.indexOf(preferences.getString("profile.role","Buyer")).coerceAtLeast(0)) }
        fields.addView(name); fields.addView(email); fields.addView(role)
        fields.addView(text("Stored only on this device. These details do not sign you in or sync to a server.",13f,false,secondary))
        val dialog=AlertDialog.Builder(this).setTitle("Local profile").setView(fields)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null)
            .setNeutralButton("Clear profile",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cleanEmail=email.text.toString().trim()
                if(cleanEmail.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                    email.error="Enter a valid email or leave it empty"; return@setOnClickListener
                }
                preferences.edit().putString("profile.name",name.text.toString().trim())
                    .putString("profile.email",cleanEmail).putString("profile.role",roles[role.selectedItemPosition]).apply()
                dialog.dismiss(); render()
                Toast.makeText(this,"Profile saved on this device",Toast.LENGTH_SHORT).show()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this).setTitle("Clear local profile?")
                    .setMessage("Your saved properties will be kept. This does not delete an online account.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Clear") { _,_ ->
                        preferences.edit().remove("profile.name").remove("profile.email").remove("profile.role").apply()
                        dialog.dismiss(); render()
                    }.show()
            }
        }
        dialog.show()
    }
    private fun showError(message: String) { AlertDialog.Builder(this).setTitle("PropertyLens").setMessage(message).setPositiveButton("OK",null).show() }
    private fun rememberDraft() { if(route=="form" && ::nameInput.isInitialized) { draftName=nameInput.text.toString().trim(); draftLocation=locationInput.text.toString().trim(); draftType=types[typeInput.selectedItemPosition] } }
    override fun onSaveInstanceState(out: Bundle) { rememberDraft(); out.putString("route",route); out.putString("selected",selected); out.putString("name",draftName); out.putString("location",draftLocation); out.putString("type",draftType); super.onSaveInstanceState(out) }
    private fun back() { if(working) return; rememberDraft(); go(when(route) { "details" -> "saved"; "form" -> if(selected==null) "home" else "details"; "record" -> if(selected==null) "home" else "details"; else -> "home" }) }
    override fun onDestroy() { worker.execute { store.close() }; worker.shutdown(); super.onDestroy() }
}

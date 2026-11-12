package com.freefix.keyboard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        fun label(t: String, size: Float = 15f) = TextView(this).apply { text = t; textSize = size; setPadding(0, dp(12), 0, dp(4)) }.also { root.addView(it) }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply { text = t; setOnClickListener { onClick() } }.also { root.addView(it) }

        label("FreeFix Keyboard", 22f)
        button("1. Keyboard ఆన్ చేయండి") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button("2. FreeFix ని ఎంచుకోండి") { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        label("3. ఫ్రీ Gemini API key")
        button("Key తీసుకోండి (aistudio.google.com)") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }
        val keyEt = EditText(this).apply {
            hint = "API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Prefs.apiKey(this@SettingsActivity))
        }.also { root.addView(it) }
        label("Model పేరు")
        val modelEt = EditText(this).apply { setText(Prefs.model(this@SettingsActivity)) }.also { root.addView(it) }
        label("ఫలితం భాష")
        val spin = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Auto (అదే భాష; Tenglish → తెలుగు)", "తెలుగు", "English"))
            setSelection(Prefs.outLang(this@SettingsActivity))
        }.also { root.addView(it) }
        val result = TextView(this).apply { textSize = 16f; setPadding(0, dp(8), 0, dp(8)) }
        fun save() = Prefs.save(this, keyEt.text.toString(), modelEt.text.toString(), spin.selectedItemPosition)
        button("Save") { save(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show() }
        button("Test") {
            save(); result.text = "⏳"
            GeminiClient.run(this, Mode.FIX, "i has went to market yesterday and buyed apple") { r -> result.text = r.getOrElse { it.message ?: "లోపం" } }
        }
        root.addView(result)
        label("గమనిక: మీ టెక్స్ట్ Fix / Formal / Casual నొక్కినప్పుడు మాత్రమే Google Gemini కి వెళ్తుంది, యాప్ ఏదీ సేవ్ చేయదు. ఫ్రీ key అయితే Google ఆ రిక్వెస్ట్‌లను తన ఉత్పత్తుల మెరుగుదలకు వాడుకోవచ్చు, కాబట్టి ప్రైవేట్ టెక్స్ట్‌కి AI వాడొద్దు. పాస్‌వర్డ్ ఫీల్డ్‌లలో AI ఆటోమేటిక్‌గా ఆఫ్ అవుతుంది.", 13f)
        setContentView(ScrollView(this).apply { addView(root) })
    }
}

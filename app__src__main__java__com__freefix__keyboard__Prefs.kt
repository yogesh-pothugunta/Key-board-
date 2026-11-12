package com.freefix.keyboard

import android.content.Context

object Prefs {
    const val DEFAULT_MODEL = "gemini-2.5-flash-lite"
    private fun sp(c: Context) = c.getSharedPreferences("freefix", Context.MODE_PRIVATE)
    fun apiKey(c: Context): String = sp(c).getString("key", "")!!.trim()
    fun model(c: Context): String = sp(c).getString("model", DEFAULT_MODEL)!!.trim().ifBlank { DEFAULT_MODEL }
    /** 0 = auto (same language; Tenglish -> Telugu), 1 = Telugu, 2 = English */
    fun outLang(c: Context): Int = sp(c).getInt("out", 0)
    fun save(c: Context, key: String, model: String, out: Int) {
        sp(c).edit().putString("key", key.trim()).putString("model", model.trim()).putInt("out", out).apply()
    }
}

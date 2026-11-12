package com.freefix.keyboard

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.ScrollingMovementMethod
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView

class AiKeyboardService : InputMethodService() {
    private companion object {
        const val MP = ViewGroup.LayoutParams.MATCH_PARENT
        val BG = Color.parseColor("#1B1E23"); val KEY = Color.parseColor("#3B414A")
        val SPECIAL = Color.parseColor("#2A2F36"); val ACCENT = Color.parseColor("#3D7BE0"); val GREEN = Color.parseColor("#2E8B57")
        const val MAX_CHARS = 4000
    }

    private class Src(val text: String, val useSel: Boolean, val before: Int, val after: Int)
    private class Pending(val orig: String, val result: String, val useSel: Boolean)

    private val handler = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private lateinit var keysBox: LinearLayout
    private lateinit var previewBar: LinearLayout
    private lateinit var previewText: TextView
    private lateinit var applyBtn: TextView
    private lateinit var undoBtn: TextView
    private val aiBtns = mutableListOf<TextView>()
    private var shift = false; private var caps = false; private var symbols = false
    private var passwordField = false; private var busy = false
    private var pending: Pending? = null
    private var lastOld: String? = null; private var lastNew: String? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(label: String, weight: Float, color: Int, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 17f
        background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(color) }
        layoutParams = LinearLayout.LayoutParams(0, MP, weight).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
    }

    private fun row(h: Int) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(MP, h) }

    override fun onCreateInputView(): View {
        val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); setPadding(dp(4), dp(4), dp(4), dp(6)) }
        val bar = row(dp(42))
        fun ai(label: String, w: Float, mode: Mode) { val b = btn(label, w, SPECIAL) { runAi(mode) }; b.textSize = 13f; aiBtns.add(b); bar.addView(b) }
        ai("Fix", 1f, Mode.FIX); ai("Formal", 1.2f, Mode.FORMAL); ai("Casual", 1.2f, Mode.CASUAL)
        undoBtn = btn("↶ Undo", 1.2f, SPECIAL) { undo() }.also { it.textSize = 13f }
        bar.addView(undoBtn)
        bar.addView(btn("⚙", 0.7f, SPECIAL) { startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        r.addView(bar)

        previewBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; visibility = View.GONE; minimumHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(MP, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        previewText = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; maxLines = 5; movementMethod = ScrollingMovementMethod()
            setPadding(dp(8), dp(6), dp(8), dp(6)); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 6f)
        }
        applyBtn = btn("✓", 1f, GREEN) { applyPending() }
        previewBar.addView(previewText); previewBar.addView(applyBtn); previewBar.addView(btn("✕", 1f, SPECIAL) { hidePreview() })
        r.addView(previewBar)

        keysBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        r.addView(keysBox)
        root = r
        renderKeys(); refreshAiState()
        return r
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        passwordField = isPassword(info); symbols = false; shift = false
        if (!restarting) { lastNew = null; lastOld = null }
        if (root != null) { hidePreview(); refreshAiState(); renderKeys(); updateCaps() }
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun isPassword(i: EditorInfo): Boolean {
        val c = i.inputType and InputType.TYPE_MASK_CLASS; val v = i.inputType and InputType.TYPE_MASK_VARIATION
        return (c == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
            (c == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    // ---------- keys ----------
    private fun renderKeys() {
        keysBox.removeAllViews()
        val upper = !symbols && (shift || caps)
        val rows = if (symbols) listOf("1234567890", "@#\$_&-+()/", "*\"':;!?%") else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        keysBox.addView(charRow(rows[0], upper, 0f))
        keysBox.addView(charRow(rows[1], upper, if (symbols) 0f else 0.5f))
        val r3 = row(dp(46))
        if (!symbols) {
            val s = btn(if (caps) "⇪" else "⇧", 1.5f, if (shift || caps) ACCENT else SPECIAL) { if (caps) { caps = false; shift = false } else shift = !shift; renderKeys() }
            s.setOnLongClickListener { caps = !caps; shift = caps; renderKeys(); true }
            r3.addView(s)
        }
        rows[2].forEach { r3.addView(key(it, upper)) }
        r3.addView(backspace())
        keysBox.addView(r3)
        val r4 = row(dp(46))
        r4.addView(btn(if (symbols) "ABC" else "123", 1.5f, SPECIAL) { symbols = !symbols; renderKeys() })
        r4.addView(btn("🌐", 1f, SPECIAL) { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() })
        r4.addView(btn(",", 1f, KEY) { type(",") })
        r4.addView(btn("space", 4f, KEY) { type(" ") })
        r4.addView(btn(".", 1f, KEY) { type(".") })
        r4.addView(btn("↵", 1.5f, ACCENT) { enter() })
        keysBox.addView(r4)
    }

    private fun charRow(chars: String, upper: Boolean, side: Float) = row(dp(46)).apply {
        if (side > 0) addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, MP, side) })
        chars.forEach { addView(key(it, upper)) }
        if (side > 0) addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, MP, side) })
    }

    private fun key(c: Char, upper: Boolean): TextView { val s = if (upper) c.uppercaseChar().toString() else c.toString(); return btn(s, 1f, KEY) { type(s) } }

    private fun backspace(): TextView {
        val b = btn("⌫", 1.5f, SPECIAL) {}
        val rep = object : Runnable { override fun run() { del(); handler.postDelayed(this, 60) } }
        b.setOnClickListener(null)
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); del(); handler.postDelayed(rep, 400) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(rep)
            }
            true
        }
        return b
    }

    private fun type(s: String) { currentInputConnection?.commitText(s, 1); updateCaps() }
    private fun del() { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); updateCaps() }

    private fun enter() {
        val ei = currentInputEditorInfo ?: return
        val a = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        if ((ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 || a == EditorInfo.IME_ACTION_NONE || a == EditorInfo.IME_ACTION_UNSPECIFIED)
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) else currentInputConnection?.performEditorAction(a)
    }

    private fun updateCaps() {
        if (caps || symbols) return
        val ei = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val want = ic.getCursorCapsMode(ei.inputType) != 0
        if (want != shift) { shift = want; renderKeys() }
    }

    // ---------- AI ----------
    private fun refreshAiState() {
        aiBtns.forEach { it.alpha = if (passwordField) 0.35f else 1f }
        undoBtn.alpha = if (lastNew != null && !passwordField) 1f else 0.5f
    }

    private fun readSource(ic: InputConnection, announce: Boolean): Src? {
        val sel = ic.getSelectedText(0)?.toString()
        if (!sel.isNullOrEmpty()) {
            if (sel.length > MAX_CHARS) { if (announce) showMessage("టెక్స్ట్ చాలా పెద్దది, కొంత భాగం సెలెక్ట్ చేయండి"); return null }
            return Src(sel, true, 0, 0)
        }
        val b = ic.getTextBeforeCursor(5000, 0)?.toString() ?: ""
        val a = ic.getTextAfterCursor(5000, 0)?.toString() ?: ""
        if (b.length + a.length > MAX_CHARS) { if (announce) showMessage("టెక్స్ట్ చాలా పెద్దది, కొంత భాగం సెలెక్ట్ చేయండి"); return null }
        if ((b + a).isBlank()) { if (announce) showMessage("ముందు ఏదైనా టైప్ చేయండి"); return null }
        return Src(b + a, false, b.length, a.length)
    }

    private fun runAi(mode: Mode) {
        if (passwordField) { showMessage("పాస్‌వర్డ్ ఫీల్డ్‌లో AI పనిచేయదు"); return }
        if (busy) return
        val ic = currentInputConnection ?: return
        val src = readSource(ic, true) ?: return
        busy = true; showMessage("⏳ …")
        GeminiClient.run(this, mode, src.text) { res ->
            busy = false
            res.onSuccess { out ->
                if (out.isBlank()) showMessage("ఫలితం రాలేదు, మళ్ళీ ప్రయత్నించండి") else {
                    pending = Pending(src.text, out, src.useSel)
                    previewText.text = diff(src.text, out)
                    applyBtn.visibility = View.VISIBLE; previewBar.visibility = View.VISIBLE
                }
            }
            res.onFailure { showMessage(it.message ?: "లోపం") }
        }
    }

    private fun applyPending() {
        val p = pending ?: return
        val ic = currentInputConnection ?: return
        val now = readSource(ic, false)
        if (now == null || now.text != p.orig || now.useSel != p.useSel) { showMessage("టెక్స్ట్ మారింది, మళ్ళీ ప్రయత్నించండి"); return }
        ic.beginBatchEdit()
        if (!p.useSel) ic.deleteSurroundingText(now.before, now.after)
        ic.commitText(p.result, 1)
        ic.endBatchEdit()
        lastOld = p.orig; lastNew = p.result
        hidePreview(); refreshAiState(); updateCaps()
    }

    private fun undo() {
        val n = lastNew ?: return; val o = lastOld ?: return
        val ic = currentInputConnection ?: return
        if (ic.getTextBeforeCursor(n.length, 0)?.toString() == n) {
            ic.beginBatchEdit(); ic.deleteSurroundingText(n.length, 0); ic.commitText(o, 1); ic.endBatchEdit()
            lastNew = null; lastOld = null; hidePreview(); refreshAiState()
        } else showMessage("Undo చేయలేము, టెక్స్ట్ మారింది")
    }

    private fun showMessage(msg: String) { previewBar.visibility = View.VISIBLE; previewText.text = msg; applyBtn.visibility = View.GONE; pending = null }
    private fun hidePreview() { if (::previewBar.isInitialized) { previewBar.visibility = View.GONE; pending = null } }

    /** Highlights words in the new text that differ from the old text. */
    private fun diff(old: String, new: String): CharSequence {
        val a = old.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val b = new.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (a.size > 300 || b.size > 300) return new
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.size - 1 downTo 0) for (j in b.size - 1 downTo 0)
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        val keep = BooleanArray(b.size); var i = 0; var j = 0
        while (i < a.size && j < b.size) {
            if (a[i] == b[j]) { keep[j] = true; i++; j++ } else if (dp[i + 1][j] >= dp[i][j + 1]) i++ else j++
        }
        val sb = SpannableStringBuilder()
        b.forEachIndexed { idx, w ->
            val s = sb.length; sb.append(w)
            if (!keep[idx]) sb.setSpan(BackgroundColorSpan(Color.parseColor("#663FA34D")), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (idx < b.size - 1) sb.append(' ')
        }
        return sb
    }
}

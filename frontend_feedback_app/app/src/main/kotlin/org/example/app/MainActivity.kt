package org.example.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*

/**
 * MainActivity implements a feedback form with two fields (name and feedback),
 * persists submissions using SharedPreferences, and displays all saved feedback
 * in reverse chronological order. The UI follows the Ocean Professional theme:
 * blue and amber accents, rounded corners, subtle shadows, and a clean layout.
 */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var listView: ListView
    private lateinit var nameInput: EditText
    private lateinit var feedbackInput: EditText
    private lateinit var submitButton: Button
    private lateinit var adapter: FeedbackAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set content view for our custom layout
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

        // Bind views
        listView = findViewById(R.id.feedback_list)
        nameInput = findViewById(R.id.input_name)
        feedbackInput = findViewById(R.id.input_feedback)
        submitButton = findViewById(R.id.button_submit)

        // Load saved feedback and show it latest-first
        val items = loadFeedback()
        adapter = FeedbackAdapter(this, items)
        listView.adapter = adapter
        listView.isNestedScrollingEnabled = true

        // Enable/disable submit based on validation
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                submitButton.isEnabled = isFormValid()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        nameInput.addTextChangedListener(watcher)
        feedbackInput.addTextChangedListener(watcher)

        // Submit on IME Done in feedback input for convenience
        feedbackInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                if (isFormValid()) {
                    onSubmit()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }

        submitButton.isEnabled = isFormValid()
        submitButton.setOnClickListener { onSubmit() }
    }

    private fun isFormValid(): Boolean {
        return nameInput.text?.toString()?.trim()?.isNotEmpty() == true &&
               feedbackInput.text?.toString()?.trim()?.isNotEmpty() == true
    }

    private fun onSubmit() {
        val name = nameInput.text.toString().trim()
        val feedback = feedbackInput.text.toString().trim()
        if (name.isEmpty() || feedback.isEmpty()) {
            showToast(R.string.error_required_fields)
            return
        }

        // Create model with current timestamp for ordering
        val item = FeedbackItem(
            id = System.currentTimeMillis(),
            name = name,
            feedback = feedback
        )

        // Persist (prepend) and update UI
        val list = loadFeedback()
        list.add(0, item)
        saveFeedback(list)
        adapter.setItems(list)

        // Clear inputs and give a success indication
        nameInput.text?.clear()
        feedbackInput.text?.clear()
        showToast(R.string.success_submitted)
    }

    private fun showToast(msgId: Int) {
        Toast.makeText(this, msgId, Toast.LENGTH_SHORT).show()
    }

    // Storage: JSON in SharedPreferences
    private fun loadFeedback(): MutableList<FeedbackItem> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return mutableListOf()
        return try {
            FeedbackStorageCodec.decode(raw)
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveFeedback(items: List<FeedbackItem>) {
        val json = FeedbackStorageCodec.encode(items)
        prefs.edit().putString(KEY_ENTRIES, json).apply()
    }

    companion object {
        private const val PREFS_FILE = "feedback_prefs"
        private const val KEY_ENTRIES = "entries"
    }
}

/**
 * Simple data class representing a feedback entry.
 */
data class FeedbackItem(
    val id: Long,
    val name: String,
    val feedback: String
)

/**
 * Minimal JSON codec using String building and parsing to avoid extra dependencies.
 * Stores list as: [{"id":123,"name":"...","feedback":"..."}, ...]
 * Escapes quotes and backslashes.
 */
object FeedbackStorageCodec {

    fun encode(items: List<FeedbackItem>): String {
        val sb = StringBuilder()
        sb.append("[")
        items.forEachIndexed { index, item ->
            if (index > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":").append(item.id).append(",")
            sb.append("\"name\":\"").append(escape(item.name)).append("\",")
            sb.append("\"feedback\":\"").append(escape(item.feedback)).append("\"")
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    fun decode(json: String): MutableList<FeedbackItem> {
        val result = mutableListOf<FeedbackItem>()
        // Very small permissive parser for our known structure.
        // Assumes array of objects with id (number), name (string), feedback (string).
        val trimmed = json.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return result

        // Split top-level objects by commas not inside braces or quotes.
        var i = 0
        if (trimmed[i] != '[') return result
        i++ // skip [

        val itemsRaw = mutableListOf<StringBuilder>()
        var current = StringBuilder()
        var depth = 0
        var inString = false
        var escapeNext = false

        while (i < trimmed.length) {
            val c = trimmed[i]
            if (escapeNext) {
                current.append(c)
                escapeNext = false
            } else {
                when (c) {
                    '\\' -> {
                        current.append(c)
                        if (inString) escapeNext = true
                    }
                    '"' -> {
                        inString = !inString
                        current.append(c)
                    }
                    '{' -> {
                        depth++
                        current.append(c)
                    }
                    '}' -> {
                        depth--
                        current.append(c)
                        if (depth == 0 && !inString) {
                            itemsRaw.add(current)
                            current = StringBuilder()
                        }
                    }
                    ',' -> {
                        if (depth > 0 || inString) {
                            current.append(c)
                        }
                        // else: top-level item separator, skip
                    }
                    ']' -> {
                        // end of array
                        break
                    }
                    else -> current.append(c)
                }
            }
            i++
        }

        for (sb in itemsRaw) {
            parseObject(sb.toString())?.let { result.add(it) }
        }
        return result
    }

    private fun parseObject(obj: String): FeedbackItem? {
        // Extract fields using simple scans
        val id = extractNumber(obj, "id") ?: return null
        val name = extractString(obj, "name") ?: return null
        val feedback = extractString(obj, "feedback") ?: return null
        return FeedbackItem(id, name, feedback)
    }

    private fun extractNumber(source: String, key: String): Long? {
        val k = "\"$key\":"
        val idx = source.indexOf(k)
        if (idx == -1) return null
        var i = idx + k.length
        // read number until non-digit
        val sb = StringBuilder()
        while (i < source.length && (source[i].isDigit() || source[i] == '-')) {
            sb.append(source[i])
            i++
        }
        return sb.toString().toLongOrNull()
    }

    private fun extractString(source: String, key: String): String? {
        val k = "\"$key\":\""
        val start = source.indexOf(k)
        if (start == -1) return null
        var i = start + k.length
        val sb = StringBuilder()
        var escapeNext = false
        while (i < source.length) {
            val c = source[i]
            if (escapeNext) {
                when (c) {
                    '\\', '"' -> sb.append(c)
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    else -> sb.append(c)
                }
                escapeNext = false
            } else {
                when (c) {
                    '\\' -> escapeNext = true
                    '"' -> return sb.toString()
                    else -> sb.append(c)
                }
            }
            i++
        }
        return null
    }

    private fun escape(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}

/**
 * Adapter to render feedback items in reverse chronological order (list already provided as such).
 * Applies minimalist card styling via list item layout.
 */
class FeedbackAdapter(
    private val ctx: Context,
    private var items: MutableList<FeedbackItem>
) : BaseAdapter() {

    fun setItems(newItems: MutableList<FeedbackItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
        val view = convertView ?: View.inflate(ctx, R.layout.item_feedback, null)
        val nameView = view.findViewById<TextView>(R.id.item_name)
        val feedbackView = view.findViewById<TextView>(R.id.item_feedback)
        val badge = view.findViewById<TextView>(R.id.item_badge)

        val item = items[position]
        nameView.text = item.name
        feedbackView.text = item.feedback
        badge.text = "NEW"

        // First item shows badge; others hide
        badge.visibility = if (position == 0) View.VISIBLE else View.GONE

        return view
    }
}

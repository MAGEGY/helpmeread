package com.helpmeread.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(
    val id: Long,
    val text: String,
    val timestamp: Long,
    val languageCode: String,
    val blockCount: Int
)

class HistoryManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("helpmeread_history", Context.MODE_PRIVATE)
    private var idCounter = 0

    fun save(text: String, languageCode: String, blockCount: Int): HistoryItem {
        val item = HistoryItem(
            // Unique within and across saves even in the same millisecond
            id = System.currentTimeMillis() * 1000 + (idCounter++ % 1000),
            text = text,
            timestamp = System.currentTimeMillis(),
            languageCode = languageCode,
            blockCount = blockCount
        )
        val items = getItems().toMutableList()
        items.add(0, item)
        // Keep only the last 50 items to avoid unbounded growth
        val trimmed = if (items.size > 50) items.take(50) else items
        saveItems(trimmed)
        return item
    }

    fun getItems(): List<HistoryItem> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                HistoryItem(
                    id = obj.getLong("id"),
                    text = obj.getString("text"),
                    timestamp = obj.getLong("timestamp"),
                    languageCode = obj.getString("languageCode"),
                    blockCount = obj.getInt("blockCount")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteItem(id: Long) {
        val items = getItems().filterNot { it.id == id }
        saveItems(items)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveItems(items: List<HistoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("timestamp", item.timestamp)
                put("languageCode", item.languageCode)
                put("blockCount", item.blockCount)
            })
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val KEY_HISTORY = "history_items"

        fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
}

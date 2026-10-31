package com.example.pdfreader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject

data class RecentDocument(val uri: String, val name: String, val openedAt: Long)

object Storage {
    private const val PREFS = "pdf_reader_prefs"
    private const val KEY_RECENTS = "recents_json"
    private const val KEY_LAST_PAGES = "last_pages_json"
    private const val KEY_BOOKMARKS = "bookmarks_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun displayName(context: Context, uri: Uri): String {
        var name = "Document.pdf"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)?.let { name = it }
            }
        }
        return name
    }

    // ---- Recent documents (most recent first, max 20) ----

    fun addRecent(context: Context, uri: Uri, name: String) {
        val list = getRecents(context).toMutableList()
        list.removeAll { it.uri == uri.toString() }
        list.add(0, RecentDocument(uri.toString(), name, System.currentTimeMillis()))
        val trimmed = list.take(20)
        val array = JSONArray()
        trimmed.forEach { doc ->
            array.put(JSONObject().apply {
                put("uri", doc.uri)
                put("name", doc.name)
                put("openedAt", doc.openedAt)
            })
        }
        prefs(context).edit().putString(KEY_RECENTS, array.toString()).apply()
    }

    fun getRecents(context: Context): List<RecentDocument> {
        val raw = prefs(context).getString(KEY_RECENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                RecentDocument(
                    uri = obj.getString("uri"),
                    name = obj.optString("name", "Document.pdf"),
                    openedAt = obj.optLong("openedAt", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---- Last read page per document ----

    private fun readMap(context: Context, key: String): JSONObject {
        val raw = prefs(context).getString(key, null) ?: return JSONObject()
        return try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
    }

    private fun writeMap(context: Context, key: String, map: JSONObject) {
        prefs(context).edit().putString(key, map.toString()).apply()
    }

    fun saveLastPage(context: Context, uri: Uri, page: Int) {
        val map = readMap(context, KEY_LAST_PAGES)
        map.put(uri.toString(), page)
        writeMap(context, KEY_LAST_PAGES, map)
    }

    fun getLastPage(context: Context, uri: Uri): Int {
        return readMap(context, KEY_LAST_PAGES).optInt(uri.toString(), 0)
    }

    // ---- Bookmarks per document (0-based page numbers) ----

    fun getBookmarks(context: Context, uri: Uri): List<Int> {
        val map = readMap(context, KEY_BOOKMARKS)
        val raw = map.optJSONArray(uri.toString()) ?: return emptyList()
        return List(raw.length()) { raw.optInt(it) }.sorted()
    }

    fun toggleBookmark(context: Context, uri: Uri, page: Int): Boolean {
        val current = getBookmarks(context, uri).toMutableList()
        val nowBookmarked = if (current.contains(page)) {
            current.remove(page); false
        } else {
            current.add(page); true
        }
        val map = readMap(context, KEY_BOOKMARKS)
        val array = JSONArray()
        current.sorted().forEach { array.put(it) }
        map.put(uri.toString(), array)
        writeMap(context, KEY_BOOKMARKS, map)
        return nowBookmarked
    }

    fun isBookmarked(context: Context, uri: Uri, page: Int): Boolean =
        getBookmarks(context, uri).contains(page)
}

package pink.yussp.dates

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object Store {
    private const val FILE = "dates.json"
    private const val PREFS = "dates_prefs"

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Entry> {
        val file = ctx.getFileStreamPath(FILE)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { Entry.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, items: List<Entry>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        ctx.openFileOutput(FILE, Context.MODE_PRIVATE).use { it.write(arr.toString().toByteArray()) }
    }

    fun exportTo(ctx: Context, uri: Uri, items: List<Entry>): Boolean {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        val root = JSONObject()
            .put("app", "dates")
            .put("savedAt", Instant.now().toString())
            .put("items", arr)
        return try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) } != null
        } catch (e: Exception) {
            false
        }
    }

    fun importFrom(ctx: Context, uri: Uri): List<Entry>? {
        return try {
            val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return null
            val arr = JSONObject(text).optJSONArray("items") ?: return null
            val out = mutableListOf<Entry>()
            for (i in 0 until arr.length()) {
                val e = Entry.fromJson(arr.getJSONObject(i))
                if (e.name.isNotBlank() && isValidDate(e.date)) out.add(e)
            }
            out
        } catch (e: Exception) {
            null
        }
    }
}

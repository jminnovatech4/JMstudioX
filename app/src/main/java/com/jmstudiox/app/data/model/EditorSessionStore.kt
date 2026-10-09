package com.jmstudiox.app.data.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small crash-safe session journal stored in app-private preferences. */
object EditorSessionStore {
    private const val PREFS = "jmstudiox_editor_session"
    private const val KEY_SESSION = "active_session_v1"

    fun load(context: Context): EditorSession? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SESSION, null)
            ?: return null
        val o = JSONObject(raw)
        val edit = o.optJSONObject("edit") ?: JSONObject()
        val crop = edit.optString("crop", "").takeIf { it.isNotBlank() && it != "null" }
        val overlay = edit.optJSONObject("textOverlay")
        val clipsJson = o.optJSONArray("clips") ?: JSONArray()
        val clips = buildList { for (i in 0 until clipsJson.length()) add(clipsJson.optString(i)) }
        EditorSession(
            sourceUri = o.getString("sourceUri"),
            fileName = o.optString("fileName", "Video"),
            editState = EditState(
                startMs = edit.optLong("startMs", 0L), endMs = edit.optLong("endMs", Long.MAX_VALUE),
                speed = edit.optDouble("speed", 1.0).toFloat(), volume = edit.optDouble("volume", 1.0).toFloat(),
                brightness = edit.optDouble("brightness", 0.0).toFloat(), contrast = edit.optDouble("contrast", 1.0).toFloat(),
                saturation = edit.optDouble("saturation", 1.0).toFloat(), crop = crop?.let { CropState(it) },
                rotation = edit.optInt("rotation", 0),
                textOverlay = overlay?.let { TextOverlayState(it.optString("text", ""), it.optDouble("x", .5).toFloat(), it.optDouble("y", .85).toFloat(), it.optInt("fontSize", 48)) },
                watermarkPath = edit.optString("watermarkPath", "").takeIf { it.isNotBlank() && it != "null" },
                mute = edit.optBoolean("mute", false)
            ), extraClipUris = clips, selectedTool = o.optString("selectedTool", "TRIM"),
            playbackPositionMs = o.optLong("playbackPositionMs", 0L), updatedAt = o.optLong("updatedAt", 0L)
        )
    }.getOrNull()

    fun save(context: Context, session: EditorSession) {
        val s = session.editState
        val edit = JSONObject().apply {
            put("startMs", s.startMs); put("endMs", s.endMs); put("speed", s.speed); put("volume", s.volume)
            put("brightness", s.brightness); put("contrast", s.contrast); put("saturation", s.saturation)
            put("crop", s.crop?.aspect); put("rotation", s.rotation); put("watermarkPath", s.watermarkPath); put("mute", s.mute)
            put("textOverlay", s.textOverlay?.let { t -> JSONObject().put("text", t.text).put("x", t.x).put("y", t.y).put("fontSize", t.fontSize) })
        }
        val clips = JSONArray().apply { session.extraClipUris.forEach { put(it) } }
        val root = JSONObject().put("sourceUri", session.sourceUri).put("fileName", session.fileName)
            .put("edit", edit).put("clips", clips).put("selectedTool", session.selectedTool)
            .put("playbackPositionMs", session.playbackPositionMs).put("updatedAt", System.currentTimeMillis())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SESSION, root.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_SESSION).apply()
    }
}

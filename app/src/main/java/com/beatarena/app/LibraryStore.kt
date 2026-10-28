package com.beatarena.app

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object LibraryStore {
    private const val PREFERENCES = "beatarena-library"
    private const val TRACKS = "tracks"

    fun read(context: Context): List<AudioTrack> {
        val encoded = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(TRACKS, "[]")
            ?: "[]"
        return try {
            val array = JSONArray(encoded)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                AudioTrack(
                    uri = item.getString("uri"),
                    title = item.getString("title"),
                    artist = item.getString("artist"),
                    durationMs = item.optLong("durationMs"),
                )
            }
        } catch (error: JSONException) {
            Log.w("BeatArena", "Saved library metadata could not be parsed", error)
            emptyList()
        }
    }

    fun write(context: Context, tracks: List<AudioTrack>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(
                JSONObject()
                    .put("uri", track.uri)
                    .put("title", track.title)
                    .put("artist", track.artist)
                    .put("durationMs", track.durationMs),
            )
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(TRACKS, array.toString())
            .apply()
    }
}

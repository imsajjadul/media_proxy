package com.mediaproxy

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TmdbClient(context: Context) {

    private val apiKey: String
    private val baseUrl = "https://api.themoviedb.org/3"
    private val posterBase = "https://image.tmdb.org/t/p/w300"

    init {
        // Store your TMDB API key in res/values/strings.xml as <string name="tmdb_api_key">YOUR_KEY</string>
        apiKey = context.getString(R.string.tmdb_api_key)
    }

    fun searchMovie(title: String, year: String?): String? {
        return try {
            val url = buildString {
                append("$baseUrl/search/movie?api_key=$apiKey&query=")
                append(URLEncoder.encode(title, "UTF-8"))
                if (!year.isNullOrBlank()) append("&year=$year")
                append("&language=en-US")
            }

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.connect()

            if (conn.responseCode != 200) {
                Log.w("TmdbClient", "TMDB returned ${conn.responseCode}")
                conn.disconnect()
                return null
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(response)
            val results = json.getJSONArray("results")
            if (results.length() == 0) return null

            val best = results.getJSONObject(0)
            val obj = JSONObject()
                .put("title", best.optString("title", title))
                .put("year", year ?: JSONObject.NULL)
                .put("poster_path", if (best.isNull("poster_path")) JSONObject.NULL else best.getString("poster_path"))
                .put("backdrop_path", if (best.isNull("backdrop_path")) JSONObject.NULL else best.getString("backdrop_path"))
                .put("overview", best.optString("overview", ""))
                .put("vote_average", if (best.has("vote_average")) best.getDouble("vote_average") else JSONObject.NULL)
                .put("id", best.optInt("id", 0))

            obj.toString()
        } catch (e: Exception) {
            Log.e("TmdbClient", "Error: ${e.message}")
            null
        }
    }
}
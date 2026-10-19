package me.luzility.muuusic.data.model

import org.json.JSONObject

object SongJson {
    fun toJson(song: Song): JSONObject = JSONObject()
        .put("id", song.id)
        .put("title", song.title)
        .put("artist", song.artist)
        .put("duration", song.durationSec)
        .putOpt("thumbnail", song.thumbnailUrl)
        .putOpt("artistUrl", song.artistUrl)

    fun fromJson(json: JSONObject): Song = Song(
        id = json.getString("id"),
        title = json.optString("title"),
        artist = json.optString("artist"),
        durationSec = json.optLong("duration"),
        thumbnailUrl = json.optStringOrNull("thumbnail"),
        artistUrl = json.optStringOrNull("artistUrl"),
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}

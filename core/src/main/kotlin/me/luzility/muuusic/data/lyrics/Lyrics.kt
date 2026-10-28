package me.luzility.muuusic.data.lyrics

import java.util.Locale

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val synced: List<LyricLine>?, val plain: String?) {
    val isEmpty get() = synced.isNullOrEmpty() && plain.isNullOrBlank()
}

object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)

    fun parse(lrc: String): List<LyricLine> {
        val offset = offsetTag.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val tags = timeTag.findAll(raw).toList()
            if (tags.isEmpty()) continue
            val text = raw.substring(tags.last().range.last + 1).trim()
            for (tag in tags) {
                val (min, sec, frac) = tag.destructured
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.toLong()
                }
                val time = min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs - offset
                lines += LyricLine(time.coerceAtLeast(0), text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}

object TitleCleaner {
    private val brackets = Regex("""\s*[(\[【][^)\]】]*[)\]】]""")
    private val featuring = Regex("""\s+(?:ft\.?|feat\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val channelNoise = Regex("""\s*(?:-\s*Topic|VEVO|Official)$""", RegexOption.IGNORE_CASE)

    data class Query(val title: String, val artist: String)

    fun clean(rawTitle: String, rawArtist: String): Query {
        var artist = rawArtist.replace(channelNoise, "").trim()
        // "Song | Official Video" style suffixes.
        var title = rawTitle.substringBefore(" | ").replace(brackets, "").trim()

        val dash = title.indexOf(" - ")
        if (dash > 0) {
            val left = title.substring(0, dash).trim()
            val right = title.substring(dash + 3).trim()
            // Turkish locale would turn "I" into "ı"; compare with a neutral locale.
            val artistKey = artist.lowercase(Locale.ROOT)
            val leftKey = left.lowercase(Locale.ROOT)
            title = right
            if (artist.isBlank() || !(leftKey.contains(artistKey) || artistKey.contains(leftKey))) {
                artist = left
            }
        }
        title = title.replace(featuring, "").trim().trim('"', '\'', '“', '”')
        return Query(title.ifBlank { rawTitle }, artist)
    }
}

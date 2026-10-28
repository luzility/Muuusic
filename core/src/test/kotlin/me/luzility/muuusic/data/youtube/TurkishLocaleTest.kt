package me.luzility.muuusic.data.youtube

import me.luzility.muuusic.data.youtube.YouTubeRepository.Companion.forNewPipe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** Regression: NewPipe rejects music.youtube.com URLs under a Turkish default locale. */
class TurkishLocaleTest {
    private lateinit var previous: Locale

    @Before fun setUp() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
    }

    @After fun tearDown() = Locale.setDefault(previous)

    @Test fun extractsVideoIdFromMusicUrl() {
        assertEquals("dQw4w9WgXcQ", YouTubeRepository.videoIdOf("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeRepository.videoIdOf("https://www.youtube.com/watch?list=x&v=dQw4w9WgXcQ"))
    }

    @Test fun rewritesMusicHost() {
        assertEquals(
            "https://www.youtube.com/playlist?list=OLAK5uy_abc",
            "https://music.youtube.com/playlist?list=OLAK5uy_abc".forNewPipe(),
        )
    }

    @Test fun watchUrlUsesWwwHost() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", YouTubeRepository.watchUrl("dQw4w9WgXcQ"))
    }

    @Test fun extractsChannelId() {
        assertEquals(
            "UCQm-Fc8TAF3c1hJffBPjxgw",
            YouTubeRepository.channelIdOf("https://music.youtube.com/channel/UCQm-Fc8TAF3c1hJffBPjxgw?si=x"),
        )
    }
}

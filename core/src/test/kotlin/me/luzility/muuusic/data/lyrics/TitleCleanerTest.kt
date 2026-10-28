package me.luzility.muuusic.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TitleCleanerTest {
    private fun clean(title: String, artist: String) = TitleCleaner.clean(title, artist)

    @Test fun keepsCleanMusicTitles() {
        assertEquals(TitleCleaner.Query("Dudu", "Tarkan"), clean("Dudu", "Tarkan"))
    }

    @Test fun stripsVideoNoiseAndArtistPrefix() {
        assertEquals(TitleCleaner.Query("Dudu", "TARKAN"), clean("TARKAN - Dudu (Official Music Video)", "TARKAN"))
        assertEquals(TitleCleaner.Query("TRALALA", "manifest"), clean("manifest - TRALALA | Official Music Video", "manifest"))
    }

    @Test fun takesArtistFromTitleWhenChannelDiffers() {
        assertEquals(TitleCleaner.Query("Kahani Suno 2.0", "Kaifi Khalil"), clean("Kaifi Khalil - Kahani Suno 2.0 [Official Music Video]", "Artiste First"))
    }

    @Test fun dropsFeaturingAndTopicSuffix() {
        assertEquals(TitleCleaner.Query("Song", "Singer"), clean("Song ft. Someone", "Singer - Topic"))
    }

    @Test fun worksUnderTurkishLocale() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            // Matching "IMAGINE" with "Imagine" must not trip over the dotless ı.
            assertEquals(TitleCleaner.Query("BIRDS", "Imagine Dragons"), clean("IMAGINE DRAGONS - BIRDS", "Imagine Dragons"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}

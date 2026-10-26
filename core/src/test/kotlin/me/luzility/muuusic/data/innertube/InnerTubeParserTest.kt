package me.luzility.muuusic.data.innertube

import me.luzility.muuusic.data.model.BrowseItem
import me.luzility.muuusic.data.model.CollectionType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are trimmed real responses (Turkish locale), so section titles are in Turkish. */
class InnerTubeParserTest {
    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/innertube/$name.json")!!.readText())

    @Test fun homeHasMoodsAndQuickPicks() {
        val json = fixture("home")
        val feed = InnerTubeParser.home(json)
        assertEquals(3, feed.moods.size)
        assertTrue(feed.moods.all { it.title.isNotBlank() && it.params.isNotBlank() })

        val quickPicks = feed.sections.first()
        assertEquals("Hızlı seçimler", quickPicks.title)
        val song = (quickPicks.items.first() as BrowseItem.SongItem).song
        assertEquals(11, song.id.length)
        assertTrue(song.title.isNotBlank() && song.artist.isNotBlank())
        assertTrue(song.artistUrl!!.contains("/channel/UC"))

        assertTrue(feed.sections[1].items.all { it is BrowseItem.CollectionItem })
        assertNotNull(InnerTubeParser.continuation(json))
        assertNotNull(InnerTubeParser.visitorData(json))
    }

    @Test fun continuationAddsSections() {
        val sections = InnerTubeParser.homeContinuation(fixture("home_continuation"))
        assertEquals(2, sections.size)
        assertTrue(sections.all { it.items.isNotEmpty() })
    }

    @Test fun artistPageHasEverySection() {
        val page = InnerTubeParser.artist(fixture("artist"), "https://www.youtube.com/channel/UCQm-Fc8TAF3c1hJffBPjxgw")!!
        assertEquals("Tarkan", page.name)
        assertNotNull(page.subscribers)
        assertTrue(page.bannerUrl!!.isNotBlank())
        assertTrue(page.description!!.startsWith("Tarkan"))

        val titles = page.sections.map { it.title }
        assertEquals("En çok dinlenen şarkılar", titles.first())
        assertTrue("Albümler" in titles && "Benzer sanatçılar" in titles)

        val topSongs = page.sections.first()
        assertNotNull(topSongs.more)
        val albums = page.sections.first { it.title == "Albümler" }.items.map { (it as BrowseItem.CollectionItem).collection }
        assertTrue(albums.all { it.type == CollectionType.Album && it.url.contains("list=OLAK") })
        val videos = page.sections.first { it.title == "Videolar" }.items
        assertTrue(videos.all { it is BrowseItem.SongItem && it.wide })
        val similar = page.sections.first { it.title == "Benzer sanatçılar" }.items
        assertTrue(similar.all { it is BrowseItem.ArtistItem && (it as BrowseItem.ArtistItem).url.contains("/channel/UC") })
    }

    @Test fun parsesDurations() {
        assertEquals(225L, InnerTubeParser.parseDuration("3:45"))
        assertEquals(3723L, InnerTubeParser.parseDuration("1:02:03"))
        assertEquals(null, InnerTubeParser.parseDuration("3 dk"))
    }
}

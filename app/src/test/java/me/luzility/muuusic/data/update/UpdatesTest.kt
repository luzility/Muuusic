package me.luzility.muuusic.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {

    @Test
    fun releaseSortsAfterItsBetas() {
        val order = listOf("0.9.9", "1.0.0-beta.1", "1.0.0-beta.2", "1.0.0", "1.0.1-beta.1", "1.1.0")
        assertEquals(order, order.shuffled().sortedBy { Versions.code(it) })
        assertTrue(Versions.code("1.0.0-beta.10")!! > Versions.code("1.0.0-beta.9")!!)
    }

    @Test
    fun unknownTagsAreIgnored() {
        assertNull(Versions.code("nightly"))
        assertNull(Versions.code("1.0"))
        assertEquals(Versions.code("1.2.3"), Versions.code("v1.2.3"))
    }

    @Test
    fun parsesReleasesAndSkipsDrafts() {
        val json = """
            [
              {"tag_name": "v1.1.0-beta.1", "prerelease": true, "draft": false, "body": "Beta notes",
               "html_url": "https://github.com/luzility/Muuusic/releases/tag/v1.1.0-beta.1",
               "assets": [{"name": "Muuusic-1.1.0-beta.1.apk", "browser_download_url": "https://example.com/b.apk"}]},
              {"tag_name": "v1.2.0", "prerelease": false, "draft": true, "body": "", "html_url": "", "assets": []},
              {"tag_name": "v1.0.0", "prerelease": false, "draft": false, "body": " Notes ", "html_url": "https://x", "assets": []}
            ]
        """
        val releases = Updates.parse(json)
        assertEquals(listOf("1.1.0-beta.1", "1.0.0"), releases.map { it.version })
        assertEquals("https://example.com/b.apk", releases[0].apkUrl)
        assertTrue(releases[0].prerelease)
        assertNull(releases[1].apkUrl)
        assertEquals("Notes", releases[1].notes)
    }
}

package me.luzility.muuusic.ui.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistLinkTest {
    private fun id(link: String) = PlaylistsViewModel.playlistIdOf(link)

    @Test fun musicPlaylistLink() =
        assertEquals("PL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i", id("https://music.youtube.com/playlist?list=PL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i&si=x1"))

    @Test fun watchLinkWithList() =
        assertEquals("OLAK5uy_abc-DEF", id("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=OLAK5uy_abc-DEF&index=2"))

    @Test fun trimsPastedWhitespace() = assertEquals("PLxyz", id("  https://youtube.com/playlist?list=PLxyz \n"))

    @Test fun rejectsVideoLinks() = assertNull(id("https://youtu.be/dQw4w9WgXcQ"))
}

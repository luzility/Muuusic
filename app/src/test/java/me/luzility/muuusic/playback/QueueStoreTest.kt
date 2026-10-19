package me.luzility.muuusic.playback

import me.luzility.muuusic.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QueueStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = QueueStore(File(tmp.root, "queue.json"))

    private val songs = listOf(
        Song("a1", "Dudu", "Tarkan", 278, "https://img/a1", "https://www.youtube.com/channel/UC1"),
        Song("b2", "Şımarık", "Tarkan", 236, null, null),
    )

    @Test fun roundTrips() {
        val snapshot = QueueSnapshot(songs, index = 1, positionMs = 42_000, shuffle = true, repeatMode = 2)
        store().save(snapshot)
        assertEquals(snapshot, store().load())
    }

    @Test fun missingFileLoadsNothing() {
        assertNull(store().load())
    }

    @Test fun corruptFileLoadsNothing() {
        File(tmp.root, "queue.json").writeText("{not json")
        assertNull(store().load())
    }

    @Test fun clampsIndexToQueue() {
        store().save(QueueSnapshot(songs, index = 7, positionMs = -5, shuffle = false, repeatMode = 0))
        val loaded = store().load()!!
        assertEquals(1, loaded.index)
        assertEquals(0L, loaded.positionMs)
    }

    @Test fun clearRemovesQueue() {
        store().save(QueueSnapshot(songs, 0, 0, false, 0))
        store().clear()
        assertNull(store().load())
    }
}

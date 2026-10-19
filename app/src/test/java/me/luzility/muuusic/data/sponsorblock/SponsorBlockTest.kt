package me.luzility.muuusic.data.sponsorblock

import org.junit.Assert.assertEquals
import org.junit.Test

class SponsorBlockTest {

    @Test
    fun picksTheRightVideoAndDropsTinySegments() {
        val json = """
            [
              {"videoID": "other", "segments": [{"category": "sponsor", "actionType": "skip", "segment": [1, 9]}]},
              {"videoID": "kJQP7kiw5Fk", "segments": [
                {"category": "music_offtopic", "actionType": "skip", "segment": [249.38, 281.521]},
                {"category": "music_offtopic", "actionType": "skip", "segment": [0, 21.808434]},
                {"category": "sponsor", "actionType": "skip", "segment": [100, 100.4]},
                {"category": "sponsor", "actionType": "mute", "segment": [120, 130]}
              ]}
            ]
        """
        assertEquals(
            listOf(Segment(0, 21_808, "music_offtopic"), Segment(249_380, 281_521, "music_offtopic")),
            SponsorBlock.parse(json, "kJQP7kiw5Fk"),
        )
    }

    @Test
    fun missingVideoMeansNoSegments() {
        assertEquals(emptyList<Segment>(), SponsorBlock.parse("[]", "abc"))
    }
}

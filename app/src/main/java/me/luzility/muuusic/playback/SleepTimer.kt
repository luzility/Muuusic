package me.luzility.muuusic.playback

object SleepTimer {
    const val COMMAND = "me.luzility.muuusic.SLEEP_TIMER"
    const val ARG_MINUTES = "minutes"
    const val EXTRA_ENDS_AT = "sleepEndsAt"

    const val END_OF_SONG = -1

    const val ENDS_WITH_SONG = -1L

    const val FADE_MS = 8_000L
}

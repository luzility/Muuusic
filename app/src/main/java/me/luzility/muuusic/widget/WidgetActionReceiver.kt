package me.luzility.muuusic.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import me.luzility.muuusic.playback.PlaybackService

// Media3's MediaButtonReceiver only accepts "play", so the other widget buttons talk to the session directly.
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        // A receiver's own context isn't allowed to bind services.
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    when (action) {
                        PAUSE -> controller.pause()
                        NEXT -> controller.seekToNext()
                        PREVIOUS -> controller.seekToPrevious()
                    }
                    controller.release()
                }
                pending.finish()
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    companion object {
        const val PAUSE = "me.luzility.muuusic.widget.PAUSE"
        const val NEXT = "me.luzility.muuusic.widget.NEXT"
        const val PREVIOUS = "me.luzility.muuusic.widget.PREVIOUS"
    }
}

package me.luzility.muuusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.animation.PathInterpolatorCompat
import me.luzility.muuusic.playback.PlayerViewModel
import me.luzility.muuusic.ui.MuuusicRoot

class MainActivity : ComponentActivity() {

    private val player: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { splash ->
            // The icon grows and fades while the purple backdrop clears to the app.
            val icon = splash.iconView
            icon.animate().scaleX(1.4f).scaleY(1.4f).alpha(0f)
                .setDuration(EXIT_MS).setInterpolator(PathInterpolatorCompat.create(0.3f, 0f, 0.8f, 0.15f))
                .start()
            splash.view.animate().alpha(0f).setStartDelay(EXIT_MS / 3).setDuration(EXIT_MS)
                .withEndAction { splash.remove() }
                .start()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent { MuuusicRoot(player) }
    }

    private companion object {
        const val EXIT_MS = 320L
    }
}

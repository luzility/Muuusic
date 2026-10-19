package me.luzility.muuusic.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.session.MediaButtonReceiver
import me.luzility.muuusic.MainActivity
import me.luzility.muuusic.R
import java.io.File

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NowPlayingWidget()
}

class NowPlayingWidget : GlanceAppWidget() {

    // Exact, so LocalSize is the real size and the artwork can fill the height.
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme { Content(currentState()) }
        }
    }

    @Composable
    private fun Content(state: Preferences) {
        val context = LocalContext.current
        val title = state[TITLE]
        val artist = state[ARTIST].orEmpty()
        val playing = state[PLAYING] ?: false
        val artKey = state[ART_KEY]
        val art = remember(artKey) { artKey?.let { artFile(context).takeIf(File::exists)?.let { f -> BitmapFactory.decodeFile(f.path) } } }
        val wide = LocalSize.current.width >= WIDE.width

        Box(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            if (wide) {
                Row(GlanceModifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(art, GlanceModifier.size(min(LocalSize.current.height - 24.dp, LocalSize.current.width * 0.36f)))
                    Spacer(GlanceModifier.width(14.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(
                            title ?: context.getString(R.string.widget_idle),
                            maxLines = 2,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        )
                        if (artist.isNotEmpty()) {
                            Text(artist, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                        }
                        Spacer(GlanceModifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SmallButton(R.drawable.ic_widget_previous, sessionAction(context, WidgetActionReceiver.PREVIOUS))
                            Spacer(GlanceModifier.width(6.dp))
                            PlayButton(playing, GlanceModifier.size(64.dp, 48.dp))
                            Spacer(GlanceModifier.width(6.dp))
                            SmallButton(R.drawable.ic_widget_next, sessionAction(context, WidgetActionReceiver.NEXT))
                        }
                    }
                }
            } else {
                Box(GlanceModifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.BottomEnd) {
                    Artwork(art, GlanceModifier.fillMaxSize())
                    Box(GlanceModifier.padding(6.dp)) { PlayButton(playing, GlanceModifier.size(48.dp)) }
                }
            }
        }
    }

    @Composable
    private fun Artwork(art: Bitmap?, modifier: GlanceModifier) {
        if (art != null) {
            Image(ImageProvider(art), null, modifier.cornerRadius(20.dp), contentScale = ContentScale.Crop)
        } else {
            Box(modifier.cornerRadius(20.dp).background(GlanceTheme.colors.secondaryContainer)) {}
        }
    }

    @Composable
    private fun PlayButton(playing: Boolean, modifier: GlanceModifier) {
        val context = LocalContext.current
        val action = if (playing) sessionAction(context, WidgetActionReceiver.PAUSE) else play(context)
        Box(
            modifier
                .cornerRadius(if (playing) 16.dp else 24.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                null,
                GlanceModifier.size(26.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
            )
        }
    }

    @Composable
    private fun SmallButton(icon: Int, action: Action) {
        Box(
            GlanceModifier.size(44.dp).cornerRadius(22.dp).background(GlanceTheme.colors.secondaryContainer).clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(icon), null, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer))
        }
    }

    companion object {
        private val WIDE = DpSize(250.dp, 110.dp)

        private val TITLE = stringPreferencesKey("title")
        private val ARTIST = stringPreferencesKey("artist")
        private val PLAYING = booleanPreferencesKey("playing")
        private val ART_KEY = stringPreferencesKey("art")

        private fun artFile(context: Context) = File(context.filesDir, "widget_art.png")

        // Play goes through the media button receiver, which can start the service and restore the last queue.
        private fun play(context: Context): Action =
            actionSendBroadcast(
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)),
            )

        private fun sessionAction(context: Context, action: String): Action =
            actionSendBroadcast(Intent(context, WidgetActionReceiver::class.java).setAction(action))

        /** [art] is written only when [artKey] changes; pass null to keep the current picture. */
        suspend fun update(context: Context, title: String?, artist: String?, playing: Boolean, artKey: String?, art: Bitmap?) {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(NowPlayingWidget::class.java)
            if (ids.isEmpty()) return
            if (art != null) {
                val tmp = File(context.filesDir, "widget_art.tmp")
                tmp.outputStream().use { art.compress(Bitmap.CompressFormat.PNG, 100, it) }
                tmp.renameTo(artFile(context))
            }
            ids.forEach { id ->
                updateAppWidgetState(context, id) { prefs ->
                    if (title != null) prefs[TITLE] = title else prefs.remove(TITLE)
                    if (artist != null) prefs[ARTIST] = artist else prefs.remove(ARTIST)
                    prefs[PLAYING] = playing
                    if (artKey != null) prefs[ART_KEY] = artKey else prefs.remove(ART_KEY)
                }
                NowPlayingWidget().update(context, id)
            }
        }
    }
}

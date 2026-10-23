package me.luzility.muuusic.ui.settings

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.ui.graphics.vector.ImageVector
import me.luzility.muuusic.R

enum class SettingsCategory(val title: Int, val summary: Int, val icon: ImageVector) {
    Appearance(R.string.settings_appearance, R.string.settings_appearance_summary, Icons.Rounded.Palette),
    Playback(R.string.settings_playback, R.string.settings_playback_summary, Icons.Rounded.PlayCircle),
    Sound(R.string.settings_sound, R.string.settings_sound_summary, Icons.Rounded.GraphicEq),
    Lyrics(R.string.lyrics, R.string.settings_lyrics_summary, Icons.Rounded.Lyrics),
    Storage(R.string.settings_storage, R.string.settings_storage_summary, Icons.Rounded.Storage),
    Backup(R.string.settings_backup, R.string.settings_backup_summary, Icons.Rounded.Backup),
    Language(R.string.settings_language, R.string.settings_language_summary, Icons.Rounded.Language),
    Lab(R.string.settings_lab, R.string.settings_lab_summary, Icons.Rounded.Science),
    Updates(R.string.settings_updates, R.string.settings_updates_summary, Icons.Rounded.SystemUpdate),
    About(R.string.settings_about, R.string.settings_about_summary, Icons.Rounded.Info),
    Integrations(R.string.settings_integrations, R.string.settings_integrations_summary, Icons.Rounded.Hub),
    ;

    val available: Boolean get() = this != Language || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    companion object {
        val sections = listOf(
            listOf(Appearance, Playback, Sound, Lyrics),
            listOf(Storage, Backup, Language),
            listOf(Integrations, Lab),
            listOf(Updates, About),
        )
    }
}

class SettingEntry(val title: Int, val category: SettingsCategory)

val SettingsIndex = listOf(
    SettingEntry(R.string.settings_theme, SettingsCategory.Appearance),
    SettingEntry(R.string.settings_artwork_colors, SettingsCategory.Appearance),
    SettingEntry(R.string.settings_pure_black, SettingsCategory.Appearance),
    SettingEntry(R.string.settings_floating_nav, SettingsCategory.Appearance),
    SettingEntry(R.string.settings_haptics, SettingsCategory.Appearance),
    SettingEntry(R.string.settings_audio_quality, SettingsCategory.Playback),
    SettingEntry(R.string.settings_autoplay, SettingsCategory.Playback),
    SettingEntry(R.string.settings_sponsorblock, SettingsCategory.Playback),
    SettingEntry(R.string.settings_normalize, SettingsCategory.Sound),
    SettingEntry(R.string.settings_crossfade, SettingsCategory.Sound),
    SettingEntry(R.string.settings_link_pitch, SettingsCategory.Sound),
    SettingEntry(R.string.equalizer, SettingsCategory.Sound),
    SettingEntry(R.string.lyrics_text_size, SettingsCategory.Lyrics),
    SettingEntry(R.string.lyrics_alignment, SettingsCategory.Lyrics),
    SettingEntry(R.string.lyrics_blur, SettingsCategory.Lyrics),
    SettingEntry(R.string.lyrics_translate, SettingsCategory.Lyrics),
    SettingEntry(R.string.settings_cache_size, SettingsCategory.Storage),
    SettingEntry(R.string.settings_clear_cache, SettingsCategory.Storage),
    SettingEntry(R.string.settings_download_wifi, SettingsCategory.Storage),
    SettingEntry(R.string.settings_remove_downloads, SettingsCategory.Storage),
    SettingEntry(R.string.settings_clear_history, SettingsCategory.Storage),
    SettingEntry(R.string.settings_backup_create, SettingsCategory.Backup),
    SettingEntry(R.string.settings_backup_restore, SettingsCategory.Backup),
    SettingEntry(R.string.settings_app_language, SettingsCategory.Language),
    SettingEntry(R.string.settings_translate, SettingsCategory.Language),
    SettingEntry(R.string.settings_karaoke, SettingsCategory.Lab),
    SettingEntry(R.string.settings_karaoke_strength, SettingsCategory.Lab),
    SettingEntry(R.string.settings_song_recognition, SettingsCategory.Lab),
    SettingEntry(R.string.settings_discord, SettingsCategory.Integrations),
    SettingEntry(R.string.settings_check_updates, SettingsCategory.Updates),
    SettingEntry(R.string.settings_update_channel, SettingsCategory.Updates),
    SettingEntry(R.string.settings_tour, SettingsCategory.About),
    SettingEntry(R.string.settings_source, SettingsCategory.About),
    SettingEntry(R.string.settings_report, SettingsCategory.About),
    SettingEntry(R.string.settings_license, SettingsCategory.About),
    SettingEntry(R.string.settings_credits, SettingsCategory.About),
).filter { it.category.available }

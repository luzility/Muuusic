package me.luzility.muuusic.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import me.luzility.muuusic.data.model.Song

object MediaItems {
    private const val SCHEME = "muuusic"
    const val EXTRA_DURATION = "muuusic.duration"
    const val EXTRA_ARTIST_URL = "muuusic.artistUrl"

    fun uriFor(videoId: String): Uri = "$SCHEME://song/$videoId".toUri()

    fun videoIdFromUri(uri: Uri): String? =
        if (uri.scheme == SCHEME) uri.lastPathSegment else null

    // Media3 drops the URI when items cross from controller to session.
    fun withPlaybackUri(item: MediaItem): MediaItem =
        item.buildUpon()
            .setUri(uriFor(item.mediaId))
            .setCustomCacheKey(item.mediaId)
            .build()
}

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(MediaItems.uriFor(id))
        .setCustomCacheKey(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(thumbnailUrl?.toUri())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(
                    Bundle().apply {
                        putLong(MediaItems.EXTRA_DURATION, durationSec)
                        putString(MediaItems.EXTRA_ARTIST_URL, artistUrl)
                    }
                )
                .build()
        )
        .build()

fun MediaItem.toSong(): Song = Song(
    id = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    durationSec = mediaMetadata.extras?.getLong(MediaItems.EXTRA_DURATION) ?: 0,
    thumbnailUrl = mediaMetadata.artworkUri?.toString(),
    artistUrl = mediaMetadata.extras?.getString(MediaItems.EXTRA_ARTIST_URL),
)

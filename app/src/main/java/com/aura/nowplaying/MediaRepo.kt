package com.aura.nowplaying

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Preferencias simples de la app. */
object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("aura", Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = sp(ctx).getBoolean("enabled", true)
    fun setEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("enabled", v).apply()

    fun onlyApple(ctx: Context) = sp(ctx).getBoolean("only_apple", true)
    fun setOnlyApple(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("only_apple", v).apply()

    /** Segundos sin tocar antes de cerrarse solo. 0 = nunca. */
    fun autoClose(ctx: Context) = sp(ctx).getInt("auto_close", 10)
    fun setAutoClose(ctx: Context, v: Int) = sp(ctx).edit().putInt("auto_close", v).apply()
}

data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val playing: Boolean,
    val position: Long,
    val duration: Long,
    val updateTime: Long,
    val speed: Float,
    val pkg: String,
) {
    /** Posición actual estimada en milisegundos. */
    fun livePosition(): Long {
        if (!playing) return position
        val p = position + ((SystemClock.elapsedRealtime() - updateTime) * speed).toLong()
        return if (duration > 0) p.coerceIn(0, duration) else p.coerceAtLeast(0)
    }

    val sourceName: String
        get() = when (pkg) {
            MediaRepo.APPLE_MUSIC -> "Apple Music"
            "com.spotify.music" -> "Spotify"
            "com.google.android.apps.youtube.music" -> "YouTube Music"
            "deezer.android.app" -> "Deezer"
            else -> "Reproduciendo"
        }
}

/** Se conecta a la sesión de música activa (Apple Music primero) y expone su estado. */
object MediaRepo {
    const val APPLE_MUSIC = "com.apple.android.music"

    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track

    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller?.unregisterCallback(this)
            controller = null
            _track.value = null
        }
    }

    fun refresh(ctx: Context) {
        val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return
        val sessions: List<MediaController> = try {
            msm.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java))
        } catch (e: SecurityException) {
            emptyList()
        }
        val candidates = if (Prefs.onlyApple(ctx)) sessions.filter { it.packageName == APPLE_MUSIC } else sessions
        val best = candidates.firstOrNull { it.packageName == APPLE_MUSIC && it.isPlayingNow() }
            ?: candidates.firstOrNull { it.isPlayingNow() }
            ?: candidates.firstOrNull { it.packageName == APPLE_MUSIC }
            ?: candidates.firstOrNull()

        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun MediaController.isPlayingNow() = playbackState?.state == PlaybackState.STATE_PLAYING

    private fun publish() {
        val c = controller
        if (c == null) {
            _track.value = null
            return
        }
        val m = c.metadata
        val s = c.playbackState
        _track.value = Track(
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Sin título",
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            album = m?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            playing = s?.state == PlaybackState.STATE_PLAYING,
            position = s?.position ?: 0L,
            duration = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            updateTime = s?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = s?.playbackSpeed ?: 1f,
            pkg = c.packageName,
        )
    }

    fun isPlaying() = _track.value?.playing == true

    fun playPause() {
        val c = controller ?: return
        if (isPlaying()) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.transportControls?.seekTo(ms)
    }
}

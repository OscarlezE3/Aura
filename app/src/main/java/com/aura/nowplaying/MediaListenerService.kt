package com.aura.nowplaying

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat

/**
 * Servicio que Android mantiene vivo cuando le das "Acceso a notificaciones".
 * Gracias a él podemos ver qué está sonando y detectar cuando desbloqueas el teléfono.
 */
class MediaListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var registered = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { _: List<MediaController>? ->
        MediaRepo.refresh(this)
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) onUnlocked()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (registered) return
        registered = true
        try {
            getSystemService(MediaSessionManager::class.java)?.addOnActiveSessionsChangedListener(
                sessionsListener, ComponentName(this, MediaListenerService::class.java), handler
            )
        } catch (_: SecurityException) {
        }
        ContextCompat.registerReceiver(
            this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        MediaRepo.refresh(this)
    }

    override fun onListenerDisconnected() {
        cleanup()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        if (!registered) return
        registered = false
        try {
            getSystemService(MediaSessionManager::class.java)?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(unlockReceiver)
        } catch (_: Exception) {
        }
    }

    private fun onUnlocked() {
        if (!Prefs.enabled(this)) return
        if (!Settings.canDrawOverlays(this)) return
        MediaRepo.refresh(this)
        if (!MediaRepo.isPlaying()) return
        // Pequeña pausa para que termine la animación de desbloqueo del sistema
        handler.postDelayed({
            val i = Intent(this, NowPlayingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            try {
                startActivity(i)
            } catch (_: Exception) {
            }
        }, 250)
    }
}

package com.aura.nowplaying

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat

private val Ink = Color(0xFF14101F)
private val Card = Color(0xFF1F1830)
private val Pink = Color(0xFFFF3D6E)
private val Violet = Color(0xFF8A3DFF)
private val Ok = Color(0xFF3DDC97)

class MainActivity : ComponentActivity() {
    private val resumes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { SetupScreen(resumes.intValue) }
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue += 1   // vuelve a revisar los permisos al regresar de Ajustes
    }
}

private fun hasNotificationAccess(ctx: Context) =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

private fun hasOverlay(ctx: Context) = Settings.canDrawOverlays(ctx)

@Composable
private fun SetupScreen(refresh: Int) {
    val ctx = LocalContext.current
    val notif = remember(refresh) { hasNotificationAccess(ctx) }
    val overlay = remember(refresh) { hasOverlay(ctx) }
    var enabled by remember { mutableStateOf(Prefs.enabled(ctx)) }
    var onlyApple by remember { mutableStateOf(Prefs.onlyApple(ctx)) }
    var autoClose by remember { mutableIntStateOf(Prefs.autoClose(ctx)) }
    val ready = notif && overlay

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2B1A5E), Ink, Ink)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Pink, Violet, Color(0xFF2B1A5E)))),
                contentAlignment = Alignment.Center
            ) { NoteIcon(Color.White, Modifier.size(34.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("Aura", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                "Al desbloquear tu teléfono con música sonando, aparece la portada animada con tus controles y el volumen.",
                color = Color.White.copy(alpha = 0.72f), fontSize = 16.sp, lineHeight = 22.sp
            )

            Spacer(Modifier.height(26.dp))
            SectionTitle(if (ready) "Permisos listos" else "Paso 1 · Da estos dos permisos")

            PermissionCard(
                n = "1", title = "Acceso a notificaciones",
                text = "Para saber qué canción suena en Apple Music y poder pausarla o cambiarla.",
                granted = notif
            ) {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            Spacer(Modifier.height(10.dp))
            PermissionCard(
                n = "2", title = "Mostrar sobre otras apps",
                text = "Para que la pantalla aparezca sola justo después de desbloquear.",
                granted = overlay
            ) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
                )
            }
            if (!notif) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "¿El permiso sale en gris o dice “Ajuste restringido”? Ve a Ajustes › Apps › Aura, toca los tres puntos (⋮) arriba a la derecha › “Permitir ajustes restringidos”, y vuelve a intentarlo.",
                    color = Color(0xFFFFC46B), fontSize = 13.sp, lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(26.dp))
            SectionTitle("Paso 2 · Ajustes")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Card)
            ) {
                ToggleRow("Mostrar al desbloquear", "Actívalo o apágalo cuando quieras.", enabled) {
                    enabled = it; Prefs.setEnabled(ctx, it)
                }
                Divider()
                ToggleRow("Solo Apple Music", "Apágalo para que también funcione con Spotify, YouTube Music, etc.", onlyApple) {
                    onlyApple = it; Prefs.setOnlyApple(ctx, it)
                }
                Divider()
                Column(Modifier.padding(16.dp)) {
                    Text("Cerrar solo después de", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6 to "6 s", 10 to "10 s", 20 to "20 s", 0 to "Nunca").forEach { (v, label) ->
                            Chip(label, autoClose == v) { autoClose = v; Prefs.setAutoClose(ctx, v) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (notif) Brush.horizontalGradient(listOf(Pink, Violet)) else Brush.horizontalGradient(listOf(Card, Card)))
                    .clickable {
                        if (!notif) {
                            Toast.makeText(ctx, "Primero da el acceso a notificaciones", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        MediaRepo.refresh(ctx)
                        if (MediaRepo.track.value == null) {
                            Toast.makeText(ctx, "Pon una canción en Apple Music y vuelve a intentar", Toast.LENGTH_LONG).show()
                        } else {
                            ctx.startActivity(Intent(ctx, NowPlayingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Probar ahora", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Para probarlo de verdad: pon música en Apple Music, bloquea el teléfono y desbloquéalo.",
                color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
    )
}

@Composable
private fun PermissionCard(n: String, title: String, text: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .clickable(enabled = !granted, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (granted) Ok else Violet),
            contentAlignment = Alignment.Center
        ) {
            Text(if (granted) "✓" else n, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (granted) "Listo" else "Activar",
            color = if (granted) Ok else Pink, fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Pink,
                uncheckedThumbColor = Color.White.copy(alpha = 0.8f), uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Pink else Color.Transparent)
            .border(1.dp, if (selected) Pink else Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

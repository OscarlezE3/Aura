package com.aura.nowplaying

import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

class NowPlayingActivity : ComponentActivity() {

    private val opens = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        MediaRepo.refresh(this)
        setContent {
            PlayerScreen(openCount = opens.intValue, onClosed = {
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        MediaRepo.refresh(this)
        opens.intValue += 1   // vuelve a reproducir la animación de entrada
    }

    override fun onStop() {
        super.onStop()
        // Si te vas a otra app o apagas la pantalla, se cierra
        if (!isFinishing) finish()
    }
}

/** Color promedio de la portada, más vivo, para teñir el fondo. */
private fun accentFrom(b: Bitmap?): Color {
    if (b == null) return Color(0xFF8A3DFF)
    return try {
        val soft = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
        val px = Bitmap.createScaledBitmap(soft, 1, 1, true).getPixel(0, 0)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(px, hsv)
        hsv[1] = max(hsv[1], 0.45f)
        hsv[2] = max(hsv[2], 0.6f)
        Color(android.graphics.Color.HSVToColor(hsv))
    } catch (e: Exception) {
        Color(0xFF8A3DFF)
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(openCount: Int, onClosed: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val track by MediaRepo.track.collectAsState()
    val t = track

    // ---- animaciones de entrada / salida
    val enter = remember { Animatable(0f) }
    val ui = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var lastTouch by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    fun touch() {
        lastTouch = SystemClock.elapsedRealtime()
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            launch { ui.animateTo(0f, tween(180)) }
            enter.animateTo(0f, tween(300))
            onClosed()
        }
    }

    LaunchedEffect(openCount) {
        closing = false
        dragY = 0f
        touch()
        enter.snapTo(0f)
        ui.snapTo(0f)
        launch { enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 140f)) }
        delay(140)
        ui.animateTo(1f, tween(420))
    }

    // ---- cierre automático
    val autoSecs = remember { Prefs.autoClose(ctx) }
    LaunchedEffect(lastTouch, autoSecs) {
        if (autoSecs > 0) {
            delay(autoSecs * 1000L)
            close()
        }
    }

    // ---- reloj para la barra de progreso
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(t?.playing) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }

    // ---- volumen
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var volDragging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            if (!volDragging) vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        }
    }

    val accentTarget = remember(t?.art) { accentFrom(t?.art) }
    val accent by animateColorAsState(accentTarget, tween(600), label = "accent")
    val closeThreshold = with(density) { 160.dp.toPx() }

    // la portada "respira" mientras suena y se encoge en pausa (como en los teléfonos de gama alta)
    val breathe = rememberInfiniteTransition(label = "breathe")
    val pulse by breathe.animateFloat(
        initialValue = 1f, targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "pulse"
    )
    val pausedScale by animateFloatAsState(
        if (t?.playing == true) 1f else 0.88f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "paused"
    )

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragY > closeThreshold) {
                            close()
                        } else {
                            dragY = 0f
                        }
                    },
                    onDragCancel = { dragY = 0f },
                ) { change, amount ->
                    change.consume()
                    dragY = (dragY + amount).coerceAtLeast(0f)
                    touch()
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }
    ) {
        // ---- fondo: portada difuminada + degradado del color de la canción
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.55f))
        ) {
            if (t?.art != null) {
                Image(
                    bitmap = t.art.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = 1.4f; scaleY = 1.4f; alpha = 0.75f }
                        .blur(70.dp)
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )
        }

        // ---- contenido
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .graphicsLayer { translationY = dragY; alpha = 1f - (dragY / (closeThreshold * 3f)).coerceIn(0f, 0.6f) }
        ) {
            val artSize = minOf(maxWidth * 0.8f, 340.dp, maxHeight * 0.42f)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // etiqueta de la app
                Text(
                    text = (t?.sourceName ?: "Aura").uppercase(),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.graphicsLayer { alpha = ui.value }
                )
                Spacer(Modifier.height(22.dp))

                // portada con animación de entrada
                Box(
                    Modifier
                        .size(artSize)
                        .graphicsLayer {
                            val e = enter.value
                            val s = (0.55f + 0.45f * e) * pausedScale * (if (t?.playing == true) pulse else 1f)
                            scaleX = s; scaleY = s
                            rotationZ = (1f - e) * -10f
                            translationY = (1f - e) * 140.dp.toPx()
                            alpha = e.coerceIn(0f, 1f)
                        }
                        .shadow(36.dp, RoundedCornerShape(28.dp), ambientColor = accent, spotColor = accent)
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            touch(); MediaRepo.playPause()
                        }
                ) {
                    Crossfade(targetState = t?.art, animationSpec = tween(450), label = "art") { art ->
                        if (art != null) {
                            Image(
                                bitmap = art.asImageBitmap(),
                                contentDescription = "Portada",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Brush.linearGradient(listOf(Color(0xFFFF3D6E), Color(0xFF8A3DFF), Color(0xFF2B1A5E)))),
                                contentAlignment = Alignment.Center
                            ) {
                                NoteIcon(Color.White.copy(alpha = 0.9f), Modifier.size(artSize * 0.4f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))

                // textos y controles
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = ui.value
                            translationY = (1f - ui.value) * 40.dp.toPx()
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { touch() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = t?.title ?: "Nada sonando",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = listOf(t?.artist, t?.album).filter { !it.isNullOrBlank() }.joinToString(" — ")
                            .ifBlank { "Pon una canción en Apple Music" },
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(18.dp))

                    // barra de progreso
                    val dur = t?.duration ?: 0L
                    var seeking by remember { mutableStateOf<Float?>(null) }
                    val pos = remember(now, t) { t?.livePosition() ?: 0L }
                    val sliderColors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    )
                    Slider(
                        value = seeking ?: if (dur > 0) pos.toFloat() / dur else 0f,
                        onValueChange = { seeking = it; touch() },
                        onValueChangeFinished = {
                            seeking?.let { MediaRepo.seekTo((it * dur).toLong()) }
                            seeking = null
                        },
                        enabled = dur > 0,
                        colors = sliderColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val shown = seeking?.let { (it * dur).toLong() } ?: pos
                        Text(fmt(shown), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Text(if (dur > 0) "-" + fmt(dur - shown) else "", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(14.dp))

                    // botones
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PressButton(size = 64, onClick = { touch(); MediaRepo.previous() }) {
                            SkipIcon(false, Color.White, Modifier.size(34.dp))
                        }
                        PressButton(size = 84, filled = true, onClick = { touch(); MediaRepo.playPause() }) {
                            Crossfade(targetState = t?.playing == true, animationSpec = tween(200), label = "pp") { playing ->
                                if (playing) PauseIcon(Color(0xFF14101F), Modifier.size(38.dp))
                                else PlayIcon(Color(0xFF14101F), Modifier.size(38.dp))
                            }
                        }
                        PressButton(size = 64, onClick = { touch(); MediaRepo.next() }) {
                            SkipIcon(true, Color.White, Modifier.size(34.dp))
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // volumen
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SpeakerIcon(false, Color.White.copy(alpha = 0.7f), Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = vol,
                            onValueChange = {
                                volDragging = true
                                vol = it
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0)
                                touch()
                            },
                            onValueChangeFinished = { volDragging = false },
                            valueRange = 0f..maxVol.toFloat(),
                            colors = sliderColors,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        SpeakerIcon(true, Color.White.copy(alpha = 0.7f), Modifier.size(22.dp))
                    }
                }
            }

            Text(
                "Desliza hacia abajo o toca afuera para cerrar",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .graphicsLayer { alpha = ui.value }
            )
        }
    }
}

@Composable
private fun PressButton(size: Int, filled: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.84f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    Box(
        Modifier
            .size(size.dp)
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.White.copy(alpha = if (pressed) 0.18f else 0f))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

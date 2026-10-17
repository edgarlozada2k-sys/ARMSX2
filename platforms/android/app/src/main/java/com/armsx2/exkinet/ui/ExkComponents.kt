package com.armsx2.exkinet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Logo X de ExKinet (logo-exkinet.svg, viewBox 48×48). */
val ExkLogoVector: ImageVector by lazy {
    ImageVector.Builder("exkinet", 48.dp, 48.dp, 48f, 48f).apply {
        addPath(
            pathData = PathParser().parsePathString("M5 8h10l10 13L37 6h8L29 26l12 15H30L20 29 8 44H1l15-20z").toNodes(),
            fill = Brush.linearGradient(listOf(Color(0xFF7AE6FF), Color(0xFF0867FF)), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(48f, 48f)),
        )
        addPath(
            pathData = PathParser().parsePathString("M34 7h8M4 38h8").toNodes(),
            stroke = SolidColor(Color(0xFFB4F4FF)), strokeLineWidth = 2f,
        )
    }.build()
}

@Composable
fun ExkLogo(size: Dp = 40.dp, showName: Boolean = true, onClick: (() -> Unit)? = null) {
    val c = LocalExk.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp) else Modifier,
    ) {
        Image(ExkLogoVector, contentDescription = "ExKinet", modifier = Modifier.size(size).drawBehind {
            drawCircle(Color(0x551688FF), radius = this.size.minDimension * 0.62f)
        })
        if (showName) {
            Spacer(Modifier.width(10.dp))
            Text("ExKinet", color = c.text, fontSize = (size.value * 0.66f).sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

/** Borde luminoso cuando el elemento tiene el foco (mando o teclado). */
fun Modifier.exkFocusRing(focused: Boolean, shape: RoundedCornerShape, glow: Color): Modifier =
    if (!focused) this else this.drawBehind {
        val r = CornerRadius(shape.topStart.toPx(size, this), shape.topStart.toPx(size, this))
        drawRoundRect(glow.copy(alpha = 0.35f), cornerRadius = r, style = Stroke(width = 6.dp.toPx()))
        drawRoundRect(glow, cornerRadius = r, style = Stroke(width = 2.dp.toPx()))
    }

@Composable
fun ExkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: String? = null,
    minWidth: Dp = 180.dp,
    navId: String? = null,
) {
    val c = LocalExk.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(28.dp)
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(s)
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(minWidth = minWidth, minHeight = 52.dp)
            .exkNav(navId, if (enabled) onClick else null, shape = shape)
            .exkFocusRing(focused, shape, c.glow)
            .clip(shape)
            .background(c.accentBrush)
            .border(BorderStroke(1.5.dp, Color(0xFF68C5FF)), shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 14.dp),
    ) {
        Text((leading?.let { "$it  " } ?: "") + text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ExkSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    compact: Boolean = false,
    navId: String? = null,
) {
    val c = LocalExk.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(26.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(minHeight = if (compact) 40.dp else 48.dp)
            .exkNav(navId, if (enabled) onClick else null, shape = shape)
            .exkFocusRing(focused, shape, c.glow)
            .clip(shape)
            .background(c.chip)
            .border(1.dp, if (danger) c.danger.copy(alpha = 0.6f) else c.text.copy(alpha = 0.14f), shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 22.dp, vertical = if (compact) 8.dp else 12.dp),
    ) {
        Text(text, color = if (danger) c.danger else c.text, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Pestaña de la navegación superior / pestañas de sección. */
@Composable
fun ExkPill(text: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, navId: String? = null) {
    val c = LocalExk.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .exkNav(navId, onClick, shape = shape)
            .exkFocusRing(focused && !active, shape, c.glow)
            .clip(shape)
            .then(
                if (active) Modifier.background(c.navActiveBrush).border(1.dp, Color(0xFF58AAFF), shape)
                else Modifier
            )
            .clickable(interactionSource = src, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
    ) {
        Text(text, color = if (active) Color.White else c.textSecondary, fontSize = 15.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ExkPanel(modifier: Modifier = Modifier, padding: Dp = 28.dp, content: @Composable () -> Unit) {
    val c = LocalExk.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.panelBorder, shape)
            .padding(padding),
    ) { content() }
}

@Composable
fun ExkSectionTitle(eyebrow: String?, title: String, subtitle: String? = null) {
    val c = LocalExk.current
    Column {
        if (eyebrow != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
                Spacer(Modifier.width(10.dp))
                Text(eyebrow.uppercase(), color = c.accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(10.dp))
        }
        Text(title, color = c.text, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = c.textMuted, fontSize = 14.sp)
    }
}

/** Fila de ajuste con título, explicación breve y control a la derecha. */
@Composable
fun ExkRow(
    title: String,
    detail: String? = null,
    badge: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = LocalExk.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (badge != null) {
                        Spacer(Modifier.width(10.dp))
                        ExkBadge(badge)
                    }
                }
                if (detail != null) {
                    Spacer(Modifier.size(5.dp))
                    Text(detail, color = c.textMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            trailing?.invoke()
        }
        Box(Modifier.fillMaxWidth().size(height = 1.dp, width = 0.dp).background(c.text.copy(alpha = 0.07f)))
    }
}

@Composable
fun ExkBadge(text: String, warn: Boolean = false) {
    val c = LocalExk.current
    val col = if (warn) c.warning else c.accent
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(col.copy(alpha = 0.16f))
            .border(1.dp, col.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text(text, color = col, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun ExkSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, navId: String? = null) {
    val c = LocalExk.current
    Switch(
        checked = checked, onCheckedChange = onChange, enabled = enabled,
        modifier = Modifier.exkNav(navId, if (enabled) ({ onChange(!checked) }) else null, shape = RoundedCornerShape(20.dp)),
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = c.accentDeep,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF434857),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun ExkSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier, steps: Int = 0, onDone: (() -> Unit)? = null, navId: String? = null) {
    val c = LocalExk.current
    val step = (range.endInclusive - range.start) / 20f
    Slider(
        value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps,
        onValueChangeFinished = onDone,
        modifier = modifier.exkNav(navId, null,
            onLeft = { onChange((value - step).coerceIn(range)); onDone?.invoke() },
            onRight = { onChange((value + step).coerceIn(range)); onDone?.invoke() }),
        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.accent, inactiveTrackColor = c.text.copy(alpha = 0.15f)),
    )
}

/** Selector de opciones en forma de píldoras. */
@Composable
fun <T> ExkChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, navBase: String? = null) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, (v, label) -> ExkPill(label, v == selected, { onSelect(v) }, navId = navBase?.let { "$it.$i" }) }
    }
}

/** Mensaje breve en la parte inferior. */
object ExkToast {
    val message = mutableStateOf<String?>(null)
    val serial = mutableStateOf(0)
    fun show(text: String) { message.value = text; serial.value++ }
}

@Composable
fun ExkToastHost(modifier: Modifier = Modifier) {
    val c = LocalExk.current
    val msg = ExkToast.message.value
    val id = ExkToast.serial.value
    LaunchedEffect(id) { if (msg != null) { delay(3500); if (ExkToast.serial.value == id) ExkToast.message.value = null } }
    AnimatedVisibility(
        visible = msg != null, modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Box(
            Modifier.padding(24.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEEAE4)).padding(horizontal = 22.dp, vertical = 14.dp),
        ) { Text(msg.orEmpty(), color = Color(0xFF151721), fontSize = 14.sp) }
    }
}

/** Icono simple dibujado con líneas para no depender de fuentes de símbolos. */
@Composable
fun ExkDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Suppress("unused")
private val strokeCapRound = StrokeCap.Round

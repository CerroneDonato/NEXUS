package it.cerrone.dailyhub

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Nexus {
    val Text = Color(0xFFFFFFFF)
    val Text2 = Color(0xD9FFFFFF)
    val Text3 = Color(0x8CFFFFFF)
    val Glass = Color(0x24FFFFFF)
    val GlassStrong = Color(0x38FFFFFF)
    val GlassBorder = Color(0x2EFFFFFF)
    val Warn = Color(0xFFFFB199)
    val Ok = Color(0xFF7FE0A8)
    val Ink = Color(0xFF0A1220)
}

/** Same gradients as the widget backgrounds (res/drawable/bg_*.xml). */
fun skyColors(sky: Sky): List<Color> = when (sky) {
    Sky.CLEAR_DAY -> listOf(Color(0xFF1B5FC2), Color(0xFF3A8BE3), Color(0xFF5DABEB))
    Sky.CLEAR_NIGHT -> listOf(Color(0xFF060A1C), Color(0xFF111B42), Color(0xFF243163))
    Sky.CLOUDY_DAY -> listOf(Color(0xFF41536D), Color(0xFF5E7290), Color(0xFF7F92AD))
    Sky.CLOUDY_NIGHT -> listOf(Color(0xFF0F1422), Color(0xFF1B2335), Color(0xFF2B354A))
    Sky.RAIN -> listOf(Color(0xFF16202F), Color(0xFF243449), Color(0xFF35506E))
    Sky.STORM -> listOf(Color(0xFF0C0D18), Color(0xFF221E3A), Color(0xFF3B2F58))
    Sky.SNOW -> listOf(Color(0xFF4F6584), Color(0xFF6F87A6), Color(0xFF8EA3BD))
    Sky.FOG -> listOf(Color(0xFF4A5059), Color(0xFF646B76), Color(0xFF7E8590))
}

fun skyAccent(sky: Sky): Color = when (sky) {
    Sky.CLEAR_DAY -> Color(0xFFFFD36B)
    Sky.CLEAR_NIGHT -> Color(0xFFB9C4FF)
    Sky.CLOUDY_DAY, Sky.CLOUDY_NIGHT -> Color(0xFFCFE3FF)
    Sky.RAIN -> Color(0xFF8FD0FF)
    Sky.STORM -> Color(0xFFD2B4FF)
    Sky.SNOW, Sky.FOG -> Color(0xFFFFFFFF)
}

fun tempColor(celsius: Double): Color = Color(tempArgb(celsius))

@DrawableRes
fun weatherIcon(icon: String, isDay: Boolean): Int = when (icon) {
    "clear" -> if (isDay) R.drawable.ic_wx_clear_day else R.drawable.ic_wx_clear_night
    "partly-cloudy" -> if (isDay) R.drawable.ic_wx_partly_day else R.drawable.ic_wx_partly_night
    "fog" -> R.drawable.ic_wx_fog
    "drizzle", "rain" -> R.drawable.ic_wx_rain
    "storm" -> R.drawable.ic_wx_storm
    "snow" -> R.drawable.ic_wx_snow
    else -> R.drawable.ic_wx_cloudy
}

fun skyOf(snapshot: TodaySnapshot?, hour: Int): Sky =
    skyFor(snapshot?.weather?.icon, snapshot?.weather?.isDay ?: (hour in 7..17))

@Composable
fun SkyBackground(sky: Sky, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(skyColors(sky)))) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0x26FFFFFF), Color.Transparent), center = Offset(220f, 120f), radius = 900f),
            ),
        )
        content()
    }
}

private val CardShape = RoundedCornerShape(24.dp)

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x14FFFFFF))))
            .border(1.dp, Nexus.GlassBorder, CardShape)
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, accent: Color, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = modifier,
    )
}

@Composable
fun glassFieldColors(accent: Color): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Nexus.Text,
    unfocusedTextColor = Nexus.Text,
    focusedBorderColor = accent,
    unfocusedBorderColor = Nexus.GlassBorder,
    cursorColor = accent,
    focusedLabelColor = accent,
    unfocusedLabelColor = Nexus.Text3,
    focusedPlaceholderColor = Nexus.Text3,
    unfocusedPlaceholderColor = Nexus.Text3,
    focusedContainerColor = Color(0x1A000000),
    unfocusedContainerColor = Color(0x1A000000),
)

@Composable
fun nexusSwitchColors(accent: Color): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Nexus.Ink,
    checkedTrackColor = accent,
    uncheckedThumbColor = Nexus.Text2,
    uncheckedTrackColor = Nexus.Glass,
    uncheckedBorderColor = Nexus.GlassBorder,
)

/** Lilith's orb, the same glowing sphere as on the dashboard. [level] (0..1) follows the voice. */
@Composable
fun Orb(size: Dp, accent: Color, active: Boolean = false, level: Float = 0f, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(if (active) 550 else 1600), RepeatMode.Reverse),
        label = "pulse",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (active) 1200 else 4000, easing = LinearEasing)),
        label = "spin",
    )
    val voice by animateFloatAsState(level, label = "level")
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val scale = pulse + voice * 0.22f
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent), center, r), radius = r)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White, Color(0xFFB9E4FF), accent, accent.copy(alpha = 0.35f)),
                center = Offset(center.x - r * 0.2f, center.y - r * 0.25f),
                radius = r * 0.7f * scale,
            ),
            radius = r * 0.6f * scale,
        )
        rotate(spin) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, accent, Color.White)),
                startAngle = 0f,
                sweepAngle = 210f,
                useCenter = false,
                topLeft = Offset(center.x - r * 0.8f, center.y - r * 0.8f),
                size = Size(r * 1.6f, r * 1.6f),
                style = Stroke(width = r * 0.06f, cap = StrokeCap.Round),
            )
        }
    }
}

package shelli.com.myhyperioncontrol

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.*

/**
 * Interaktives Farbrad (HSV-Farbkreis).
 *
 * - Winkel  → Farbton (Hue, 0–360°)
 * - Radius  → Sättigung (0 = Mitte = Weiß, 1 = Rand = volle Farbe)
 *
 * Der Benutzer kann durch Tippen oder Ziehen eine Farbe auswählen.
 * Der ausgewählte Punkt wird durch einen kleinen Kreis markiert.
 *
 * @param hue           Aktueller Farbton (0–360°)
 * @param saturation    Aktuelle Sättigung (0–1)
 * @param onColorSelected Callback mit (hue, saturation) bei Nutzerinteraktion
 */
@Composable
fun ColorWheel(
    hue: Float,
    saturation: Float,
    modifier: Modifier = Modifier,
    onColorSelected: (hue: Float, saturation: Float) -> Unit
) {
    // Alle Farbtöne für den Sweep-Gradienten (im Uhrzeigersinn ab 3 Uhr)
    val hueColors = remember {
        listOf(
            Color(android.graphics.Color.HSVToColor(floatArrayOf(  0f, 1f, 1f))), // Rot
            Color(android.graphics.Color.HSVToColor(floatArrayOf( 60f, 1f, 1f))), // Gelb
            Color(android.graphics.Color.HSVToColor(floatArrayOf(120f, 1f, 1f))), // Grün
            Color(android.graphics.Color.HSVToColor(floatArrayOf(180f, 1f, 1f))), // Cyan
            Color(android.graphics.Color.HSVToColor(floatArrayOf(240f, 1f, 1f))), // Blau
            Color(android.graphics.Color.HSVToColor(floatArrayOf(300f, 1f, 1f))), // Magenta
            Color(android.graphics.Color.HSVToColor(floatArrayOf(360f, 1f, 1f))), // Rot (Schluss)
        )
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Berechnet Hue und Saturation aus einer Touch-Position
    fun handleTouch(offset: Offset) {
        if (canvasSize == IntSize.Zero) return
        val radius = canvasSize.width / 2f
        val center = Offset(radius, radius)
        val dx = offset.x - center.x
        val dy = offset.y - center.y
        val dist = sqrt(dx * dx + dy * dy)
        val sat = (dist / radius).coerceIn(0f, 1f)
        // atan2 liefert Winkel in Bogenmass, 0° = 3 Uhr (Ost), im Uhrzeigersinn
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0f) angle += 360f
        onColorSelected(angle, sat)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { canvasSize = it }
            // Einheitlicher Gesture-Handler für Tippen UND Ziehen
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // Warte auf erstes Drücken
                        val down = awaitPointerEvent()
                        val press = down.changes.firstOrNull() ?: continue
                        if (!press.pressed) continue
                        handleTouch(press.position)
                        press.consume()

                        // Verfolge Bewegung bis Loslassen
                        var active = true
                        while (active) {
                            val move = awaitPointerEvent()
                            move.changes.forEach { change ->
                                if (change.pressed) {
                                    handleTouch(change.position)
                                    change.consume()
                                } else {
                                    active = false
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val radius = size.width / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // 1) Hue-Ring (Sweep-Gradient über alle Farbtöne)
        drawCircle(
            brush  = Brush.sweepGradient(colors = hueColors, center = center),
            radius = radius,
            center = center
        )

        // 2) Sättigungs-Overlay: Weiß in der Mitte → transparent am Rand
        //    Ergibt zusammen mit dem Hue-Ring das klassische HSV-Farbrad
        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        // 3) Indikator für die ausgewählte Farbe
        val angleRad  = Math.toRadians(hue.toDouble())
        val indicatorX = center.x + (radius * saturation * cos(angleRad)).toFloat()
        val indicatorY = center.y + (radius * saturation * sin(angleRad)).toFloat()
        val indicatorPos = Offset(indicatorX, indicatorY)

        // Schwarzer Außenring → weißer Ring → farbige Mitte
        drawCircle(color = Color.Black, radius = 24f, center = indicatorPos)
        drawCircle(color = Color.White, radius = 21f, center = indicatorPos)
        drawCircle(
            color  = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f))),
            radius = 17f,
            center = indicatorPos
        )
    }
}

package it.tgfm.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small local vectors keep action icons clear without adding a large icon dependency. */
object UiIcons {
 private fun outline(name: String, draw: PathBuilder.() -> Unit) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
  path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { draw() }
 }.build()
 val Back = outline("Indietro") { moveTo(19f,12f); lineTo(5f,12f); moveTo(11f,6f); lineTo(5f,12f); lineTo(11f,18f) }
 val Next = outline("Apri sezione") { moveTo(9f,6f); lineTo(15f,12f); lineTo(9f,18f) }
 val Up = outline("Riduci") { moveTo(6f,15f); lineTo(12f,9f); lineTo(18f,15f) }
 val Refresh = outline("Aggiorna") { moveTo(20f,7f); curveTo(15f,0f,3f,4f,3f,12f); moveTo(20f,3f); lineTo(20f,8f); lineTo(15f,8f); moveTo(4f,17f); curveTo(9f,24f,21f,20f,21f,12f); moveTo(4f,21f); lineTo(4f,16f); lineTo(9f,16f) }
 val Eye = outline("Apri") {
  moveTo(2f,12f); curveTo(7f,3f,17f,3f,22f,12f); curveTo(17f,21f,7f,21f,2f,12f); close()
  moveTo(15f,12f); arcToRelative(3f,3f,0f,true,true,-6f,0f); arcToRelative(3f,3f,0f,true,true,6f,0f)
 }
 val Edit = outline("Modifica") { moveTo(4f,16f); lineTo(16f,4f); lineTo(20f,8f); lineTo(8f,20f); lineTo(4f,20f); close(); moveTo(13f,7f); lineTo(17f,11f) }
 val Delete = outline("Elimina") { moveTo(4f,6f); lineTo(20f,6f); moveTo(9f,6f); lineTo(9f,3f); lineTo(15f,3f); lineTo(15f,6f); moveTo(6f,6f); lineTo(7f,21f); lineTo(17f,21f); lineTo(18f,6f); moveTo(10f,10f); lineTo(10f,17f); moveTo(14f,10f); lineTo(14f,17f) }
 val Pause = outline("Disattiva") { moveTo(8f,5f); lineTo(8f,19f); moveTo(16f,5f); lineTo(16f,19f) }
 val Play = outline("Attiva") { moveTo(7f,4f); lineTo(20f,12f); lineTo(7f,20f); close() }
 val Down = outline("Espandi") { moveTo(6f,9f); lineTo(12f,15f); lineTo(18f,9f) }
 val Check = outline("Desiderato") { moveTo(5f,12f); lineTo(10f,17f); lineTo(20f,7f) }
 val Close = outline("Indesiderato") { moveTo(6f,6f); lineTo(18f,18f); moveTo(18f,6f); lineTo(6f,18f) }
 val Search = outline("Cerca") { moveTo(18f,10f); arcToRelative(7f,7f,0f,true,true,-14f,0f); arcToRelative(7f,7f,0f,true,true,14f,0f); moveTo(16f,16f); lineTo(22f,22f) }
 val Selection = outline("Selezione") { moveTo(20f,11f); lineTo(20f,20f); lineTo(4f,20f); lineTo(4f,4f); lineTo(15f,4f); moveTo(8f,11f); lineTo(12f,15f); lineTo(22f,4f) }
 val Download = outline("Download") { moveTo(12f,3f); lineTo(12f,16f); moveTo(7f,11f); lineTo(12f,16f); lineTo(17f,11f); moveTo(4f,17f); lineTo(4f,21f); lineTo(20f,21f); lineTo(20f,17f) }
 val Filters = outline("Filtri") { moveTo(4f,6f); lineTo(20f,6f); moveTo(7f,12f); lineTo(17f,12f); moveTo(10f,18f); lineTo(14f,18f) }
 val Settings = outline("Configura") { moveTo(3f,6f); lineTo(21f,6f); moveTo(3f,12f); lineTo(21f,12f); moveTo(3f,18f); lineTo(21f,18f); moveTo(8f,3f); lineTo(8f,9f); moveTo(16f,9f); lineTo(16f,15f); moveTo(8f,15f); lineTo(8f,21f) }
}

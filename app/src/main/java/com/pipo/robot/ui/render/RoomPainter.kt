package com.pipo.robot.ui.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.pipo.robot.data.ItemShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val wood = Color(0xFF7B5A45)
private val woodDark = Color(0xFF5E4434)
private val ink = Color(0xFF2B3342)

fun DrawScope.drawRoom(g: SceneGeo, camU: Float, st: RoomState, t: Float, pipoInBed: Boolean) {
    val u = g.u
    val fy = g.floorY
    val day = dayFactor(st.hour)
    val wall = lerp(Color(0xFF26304A), Color(0xFF93AEB8), day)
    val floor = lerp(Color(0xFF3F332E), Color(0xFF9A7459), day)

    drawRect(Brush.verticalGradient(listOf(lerp(wall, Color.Black, 0.25f), wall), startY = 0f, endY = fy), Offset.Zero, Size(size.width, fy))
    drawRect(Brush.verticalGradient(listOf(floor, lerp(floor, Color.Black, 0.3f)), startY = fy, endY = size.height), Offset(0f, fy), Size(size.width, size.height - fy))

    withTransform({ translate(-camU * u, 0f) }) {
        fun X(v: Float) = v * u
        fun Y(v: Float) = fy - v * u
        val worldW = X(SceneGeo.WORLD_W)

        // wall panels + baseboard + floor planks
        var px = 0f
        while (px < SceneGeo.WORLD_W) { drawLine(Color.Black.copy(alpha = 0.06f), Offset(X(px), 0f), Offset(X(px), fy), u * 0.4f); px += 30f }
        var gap = 3f; var py = fy + 2f * u
        while (py < size.height) { drawLine(Color.Black.copy(alpha = 0.12f), Offset(0f, py), Offset(worldW, py), u * 0.3f); gap *= 1.35f; py += gap * u }
        drawRect(lerp(wall, Color.Black, 0.35f), Offset(0f, fy - 2f * u), Size(worldW, 2f * u))

        // --- clock (real time)
        run {
            val c = Offset(X(62f), Y(60f))
            drawCircle(Color(0xFFF4EFE6), 5f * u, c)
            drawCircle(woodDark, 5f * u, c, style = Stroke(0.6f * u))
            val hr = st.hour % 12f
            val ha = Math.toRadians((hr / 12f * 360f - 90f).toDouble())
            val ma = Math.toRadians(((st.hour % 1f) * 360f - 90f).toDouble())
            drawLine(ink, c, Offset(c.x + cos(ha).toFloat() * 2.6f * u, c.y + sin(ha).toFloat() * 2.6f * u), 0.7f * u, StrokeCap.Round)
            drawLine(ink, c, Offset(c.x + cos(ma).toFloat() * 3.8f * u, c.y + sin(ma).toFloat() * 3.8f * u), 0.45f * u, StrokeCap.Round)
        }

        // --- drawings Pipo made for you
        for (i in 0 until st.drawings.coerceAtMost(4)) {
            val l = X(10f + i * 9f); val tp = Y(66f - (i % 2) * 3f)
            drawRect(Color(0xFFEDE3D2), Offset(l, tp), Size(7f * u, 9f * u))
            drawRect(woodDark, Offset(l, tp), Size(7f * u, 9f * u), style = Stroke(0.5f * u))
            val cx = l + 3.5f * u
            drawCircle(ink, 1.3f * u, Offset(cx, tp + 3f * u), style = Stroke(0.35f * u))
            drawLine(ink, Offset(cx, tp + 4.3f * u), Offset(cx, tp + 6.8f * u), 0.35f * u)
            drawLine(ink, Offset(cx - 1.5f * u, tp + 5f * u), Offset(cx + 1.5f * u, tp + 5.4f * u), 0.35f * u)
            // giant ears. he tried.
            drawCircle(ink, 0.7f * u, Offset(cx - 1.8f * u, tp + 2.6f * u), style = Stroke(0.3f * u))
            drawCircle(ink, 0.7f * u, Offset(cx + 1.8f * u, tp + 2.6f * u), style = Stroke(0.3f * u))
            drawCircle(PipoColors.blush, 0.6f * u, Offset(l + 5.7f * u, tp + 7.6f * u))
        }

        // --- window
        run {
            val l = X(88f); val r = X(120f); val tp = Y(74f); val b = Y(40f)
            val skyTop: Color; val skyBot: Color
            val h = st.hour
            if (h in 16.5f..20.5f) { val d = ((h - 16.5f) / 4f).coerceIn(0f, 1f)
                skyTop = lerp(Color(0xFF8EC5E8), Color(0xFF3B3F7A), d); skyBot = lerp(Color(0xFFCFE8F2), Color(0xFFF2A07B), d) }
            else { skyTop = lerp(Color(0xFF0E1733), Color(0xFF8EC5E8), day); skyBot = lerp(Color(0xFF1D2A4F), Color(0xFFCFE8F2), day) }
            drawRect(Brush.verticalGradient(listOf(skyTop, skyBot), startY = tp, endY = b), Offset(l, tp), Size(r - l, b - tp))
            if (day < 0.5f) {
                val a = 1f - day * 2f
                for (i in 0 until 14) {
                    val sx = l + ((i * 37) % 31 + 0.5f) / 31f * (r - l); val sy = tp + ((i * 53) % 29 + 0.5f) / 29f * (b - tp) * 0.8f
                    drawCircle(Color.White.copy(alpha = a * (0.4f + 0.6f * abs(sin(t * 0.7f + i)))), 0.35f * u, Offset(sx, sy))
                }
                drawCircle(Color(0xFFF4EFD8).copy(alpha = a), 3.5f * u, Offset(l + 22f * u, tp + 8f * u))
                drawCircle(skyTop.copy(alpha = a), 3.2f * u, Offset(l + 23.6f * u, tp + 7f * u))
            } else {
                drawCircle(Color(0xFFFFE6A8).copy(alpha = day), 4f * u, Offset(l + 23f * u, tp + 9f * u))
                val cx = l + ((t * 0.6f) % 45f - 8f) * u
                for ((dx, rr) in listOf(0f to 3f, 3f to 3.8f, 6.5f to 2.8f)) drawCircle(Color.White.copy(alpha = 0.85f * day), rr * u, Offset(cx + dx * u, tp + 20f * u))
            }
            drawRect(Color(0xFFEDE6DA), Offset(l, tp), Size(r - l, b - tp), style = Stroke(1.2f * u))
            drawLine(Color(0xFFEDE6DA), Offset((l + r) / 2, tp), Offset((l + r) / 2, b), 0.8f * u)
            drawLine(Color(0xFFEDE6DA), Offset(l, (tp + b) / 2), Offset(r, (tp + b) / 2), 0.8f * u)
            drawRect(Color(0xFFD8CFC2), Offset(l - 2f * u, b), Size(r - l + 4f * u, 1.6f * u))
            drawRoundRect(Color(0xFFC8B9A8).copy(alpha = 0.9f), Offset(l - 4f * u, tp - 2f * u), Size(5f * u, b - tp + 6f * u), CornerRadius(2f * u))
            drawRoundRect(Color(0xFFC8B9A8).copy(alpha = 0.9f), Offset(r - 1f * u, tp - 2f * u), Size(5f * u, b - tp + 6f * u), CornerRadius(2f * u))
            drawLine(woodDark, Offset(l - 6f * u, tp - 2f * u), Offset(r + 6f * u, tp - 2f * u), 0.8f * u, StrokeCap.Round)
        }

        // --- rug
        drawOval(Color(0xFF6E5A74).copy(alpha = 0.55f), Offset(X(78f), fy + 3f * u), Size(56f * u, 10f * u))
        drawOval(Color(0xFF8C7690).copy(alpha = 0.45f), Offset(X(84f), fy + 4.5f * u), Size(44f * u, 7f * u))

        // --- bed
        drawRoundRect(woodDark, Offset(X(3f), Y(30f)), Size(4.5f * u, 30f * u), CornerRadius(2f * u))
        drawRect(wood, Offset(X(3f), Y(9f)), Size(47f * u, 7f * u))
        drawRect(woodDark, Offset(X(46.5f), Y(4f)), Size(2.5f * u, 4f * u))
        drawRoundRect(Color(0xFFE8E1D6), Offset(X(6f), Y(SceneGeo.MATTRESS_TOP)), Size(42f * u, 5.5f * u), CornerRadius(2f * u))
        drawOval(Color(0xFFF7F2EA), Offset(X(7.5f), Y(18.5f)), Size(10f * u, 5.5f * u))
        if ("ball_on_bed" in st.pranks) drawBall(Offset(X(22f), Y(16.5f)), u)
        if (!pipoInBed) drawRoundRect(Color(0xFF6F8FA6), Offset(X(30f), Y(15.5f)), Size(18f * u, 7f * u), CornerRadius(2.2f * u))

        // --- plant
        run {
            val base = Offset(X(61f), Y(10f))
            rotate(sin(t * 0.8f) * 2.5f, pivot = base) {
                val leaves = listOf(-50f, -25f, 0f, 25f, 50f, -75f, 75f)
                for ((i, a) in leaves.withIndex()) rotate(a, pivot = base) {
                    drawOval(if (i % 2 == 0) Color(0xFF6FA27A) else Color(0xFF86B98E), Offset(base.x - 2.2f * u, base.y - 14f * u), Size(4.4f * u, 13f * u))
                }
                if ("plant_hat" in st.pranks) drawItem(ItemShape.HAT, Offset(base.x, base.y - 16.5f * u), 7f * u)
            }
            val pot = Path().apply { moveTo(X(56.5f), Y(11f)); lineTo(X(65.5f), Y(11f)); lineTo(X(64.5f), Y(0f)); lineTo(X(57.5f), Y(0f)); close() }
            drawPath(pot, Color(0xFFB97E5E))
            drawRect(Color(0xFFA06A4E), Offset(X(56f), Y(11.5f)), Size(10f * u, 1.8f * u))
        }

        // --- charging station (shows your phone's real battery)
        run {
            val pulse = if (st.charging) 0.55f + 0.45f * abs(sin(t * 2.2f)) else 0.25f
            drawOval(Color(0xFF37404F), Offset(X(75f), fy - 1f * u), Size(18f * u, 4f * u))
            drawOval(Color(0xFF7FE3F0).copy(alpha = pulse), Offset(X(76.5f), fy - 0.4f * u), Size(15f * u, 2.8f * u), style = Stroke(0.6f * u))
            drawRoundRect(Color(0xFF3B4658), Offset(X(92.5f), Y(21f)), Size(3.4f * u, 21f * u), CornerRadius(1.2f * u))
            val bars = ((st.battery + 24) / 25).coerceIn(0, 4)
            for (i in 0 until 4) {
                val on = i < bars
                drawRoundRect((if (st.battery <= 15) Color(0xFFFF8A7A) else Color(0xFF7FE3F0)).copy(alpha = if (on) (if (st.charging && i == bars - 1) pulse else 0.9f) else 0.15f),
                    Offset(X(93.2f), Y(6f + i * 3.6f)), Size(2f * u, 2.4f * u), CornerRadius(0.4f * u))
            }
            drawCircle(Color(0xFF7FE3F0).copy(alpha = pulse), 0.8f * u, Offset(X(94.2f), Y(19f)))
        }

        // --- desk, computer, lamp
        run {
            drawRect(wood, Offset(X(124f), Y(19f)), Size(32f * u, 2f * u))
            drawRect(woodDark, Offset(X(126f), Y(17f)), Size(2f * u, 17f * u))
            drawRect(woodDark, Offset(X(152f), Y(17f)), Size(2f * u, 17f * u))
            drawRect(wood, Offset(X(142f), Y(17f)), Size(10f * u, 8f * u))
            drawCircle(woodDark, 0.6f * u, Offset(X(147f), Y(13f)))
            drawRect(Color(0xFF3B4658), Offset(X(136f), Y(22f)), Size(2f * u, 3f * u))
            drawRoundRect(Color(0xFF2C3444), Offset(X(128f), Y(35f)), Size(18f * u, 13.5f * u), CornerRadius(1.5f * u))
            drawRoundRect(Color(0xFF123B3F), Offset(X(129f), Y(34f)), Size(16f * u, 11f * u), CornerRadius(0.8f * u))
            for (i in 0 until 4) {
                val len = 4f + ((i * 7 + (t * 0.5f).toInt()) % 9)
                drawLine(Color(0xFF9FE7E0).copy(alpha = 0.7f), Offset(X(130.5f), Y(32f - i * 2.3f)), Offset(X(130.5f + len), Y(32f - i * 2.3f)), 0.6f * u)
            }
            if ((t * 2f).toInt() % 2 == 0) drawRect(Color(0xFF9FE7E0), Offset(X(131f), Y(24.5f)), Size(1.2f * u, 1.3f * u))
            if ("screen_note" in st.pranks) {
                drawRect(Color(0xFFF3DB7A), Offset(X(139f), Y(33f)), Size(5.5f * u, 5.5f * u))
                drawLine(ink, Offset(X(139.8f), Y(31f)), Offset(X(143.6f), Y(31.4f)), 0.35f * u)
                drawLine(ink, Offset(X(139.8f), Y(29.5f)), Offset(X(142.6f), Y(29.7f)), 0.35f * u)
            }
            drawRoundRect(Color(0xFF3B4658), Offset(X(130f), Y(20f)), Size(12f * u, 1f * u), CornerRadius(0.4f * u))
            // books
            drawRect(Color(0xFF6F8FA6), Offset(X(146.5f), Y(22.5f)), Size(1.4f * u, 3.5f * u))
            drawRect(Color(0xFFB97E5E), Offset(X(148f), Y(23f)), Size(1.4f * u, 4f * u))
            // lamp
            drawRoundRect(Color(0xFF3B4658), Offset(X(150f), Y(20f)), Size(5f * u, 1f * u), CornerRadius(0.5f * u))
            drawLine(Color(0xFF8E99A8), Offset(X(152.5f), Y(20f)), Offset(X(150.5f), Y(30f)), 0.6f * u)
            val sock = "lamp_sock" in st.pranks
            val shade = Path().apply { moveTo(X(149f), Y(34f)); lineTo(X(152.5f), Y(34f)); lineTo(X(154f), Y(29.5f)); lineTo(X(147f), Y(29.5f)); close() }
            drawPath(shade, if (sock) Color(0xFFC99BFF) else Color(0xFFE7C27A))
            if (sock) for (i in 0..2) drawLine(Color.White.copy(alpha = 0.7f), Offset(X(148f), Y(30.5f + i * 1.3f)), Offset(X(153.5f), Y(30.5f + i * 1.3f)), 0.4f * u)
        }

        // --- shelf with discoveries
        run {
            drawRect(wood, Offset(X(162f), Y(58f)), Size(38f * u, 1.3f * u))
            drawLine(woodDark, Offset(X(165f), Y(56.7f)), Offset(X(167f), Y(54f)), 0.6f * u)
            drawLine(woodDark, Offset(X(197f), Y(56.7f)), Offset(X(195f), Y(54f)), 0.6f * u)
            for ((i, s) in st.shelf.take(8).withIndex()) drawItem(s, Offset(X(166f + i * 4.6f), Y(60.6f)), 4.2f * u)
        }

        // --- pegboard + workbench
        run {
            drawRoundRect(Color(0xFFC9B79C).copy(alpha = 0.85f), Offset(X(164f), Y(46f)), Size(34f * u, 22f * u), CornerRadius(1f * u))
            var gx = 166f
            while (gx < 197f) { var gy = 26f; while (gy < 45f) { drawCircle(Color(0xFF8E7A62).copy(alpha = 0.5f), 0.25f * u, Offset(X(gx), Y(gy))); gy += 3f }; gx += 3f }
            // wrench, screwdriver, hammer silhouettes
            drawLine(Color(0xFF8E99A8), Offset(X(170f), Y(42f)), Offset(X(170f), Y(30f)), 0.9f * u, StrokeCap.Round)
            drawCircle(Color(0xFF8E99A8), 1.6f * u, Offset(X(170f), Y(43f)), style = Stroke(0.7f * u))
            drawLine(Color(0xFFE0706A), Offset(X(178f), Y(42f)), Offset(X(178f), Y(37f)), 1.4f * u, StrokeCap.Round)
            drawLine(Color(0xFF8E99A8), Offset(X(178f), Y(37f)), Offset(X(178f), Y(30f)), 0.4f * u)
            drawLine(woodDark, Offset(X(188f), Y(42f)), Offset(X(188f), Y(30f)), 0.8f * u, StrokeCap.Round)
            drawRoundRect(Color(0xFF6B7482), Offset(X(185.5f), Y(43f)), Size(5f * u, 2f * u), CornerRadius(0.5f * u))

            drawRect(Color(0xFF8A6A52), Offset(X(160f), Y(17f)), Size(42f * u, 2.6f * u))
            drawRect(woodDark, Offset(X(162f), Y(14.4f)), Size(2f * u, 14.4f * u))
            drawRect(woodDark, Offset(X(198f), Y(14.4f)), Size(2f * u, 14.4f * u))
            drawRect(woodDark.copy(alpha = 0.8f), Offset(X(162f), Y(5f)), Size(38f * u, 1.2f * u))
            drawRect(Color(0xFF6B7482), Offset(X(161f), Y(20f)), Size(4f * u, 3f * u))
            if (st.projectActive) {
                drawItem(ItemShape.GEAR, Offset(X(170f), Y(18.8f)), 3.5f * u)
                drawItem(ItemShape.COIL, Offset(X(175f), Y(18.8f)), 3.5f * u)
                drawItem(ItemShape.SPRING, Offset(X(180f), Y(18.4f)), 3.5f * u)
            }
            st.benchThing?.let { drawItem(it, Offset(X(191f), Y(21.5f)), 7f * u) }
            if ("screw_tower" in st.pranks) for (i in 0 until 6) drawItem(ItemShape.SCREW, Offset(X(197f), Y(19f + i * 1.6f)), 2.4f * u)
        }

        // --- arcade cabinet
        run {
            drawRoundRect(Color(0xFF4A4470), Offset(X(206f), Y(46f)), Size(18f * u, 46f * u), CornerRadius(2f * u))
            drawRect(Color(0xFF3A355C), Offset(X(206f), Y(46f)), Size(2f * u, 46f * u))
            drawRoundRect(Color(0xFFFFC27A).copy(alpha = 0.8f), Offset(X(207.5f), Y(45f)), Size(15f * u, 4.5f * u), CornerRadius(1f * u))
            val scrL = X(208.5f); val scrT = Y(38f); val scrW = 13f * u; val scrH = 12f * u
            drawRect(Color(0xFF0E1320), Offset(scrL, scrT), Size(scrW, scrH))
            val speed = if (st.arcadeActive) 3f else 0.8f
            val bx = scrL + (0.5f + 0.45f * sin(t * speed)) * scrW
            val by = scrT + (0.5f + 0.4f * sin(t * speed * 1.37f)) * scrH
            drawRect(PipoColors.eye, Offset(bx - 0.5f * u, by - 0.5f * u), Size(1f * u, 1f * u))
            drawRect(PipoColors.eye.copy(alpha = 0.8f), Offset(scrL + 0.6f * u, by - 1.6f * u), Size(0.6f * u, 3.2f * u))
            drawRect(PipoColors.eye.copy(alpha = 0.8f), Offset(scrL + scrW - 1.2f * u, scrT + (0.5f + 0.4f * sin(t * speed * 1.1f)) * scrH - 1.6f * u), Size(0.6f * u, 3.2f * u))
            if ("arcade_score" in st.pranks) {
                val crown = Path().apply {
                    val cx = scrL + scrW / 2; val cy = scrT + 2.5f * u
                    moveTo(cx - 2f * u, cy + 1f * u); lineTo(cx - 2f * u, cy - 1f * u); lineTo(cx - 1f * u, cy); lineTo(cx, cy - 1.4f * u)
                    lineTo(cx + 1f * u, cy); lineTo(cx + 2f * u, cy - 1f * u); lineTo(cx + 2f * u, cy + 1f * u); close()
                }
                drawPath(crown, Color(0xFFFFC27A))
            }
            drawRect(Color(0xFF3A355C), Offset(X(207f), Y(23f)), Size(16f * u, 3f * u))
            drawLine(PipoColors.joint, Offset(X(210f), Y(24f)), Offset(X(210f), Y(27f)), 0.5f * u)
            drawCircle(Color(0xFFE0706A), 0.9f * u, Offset(X(210f), Y(27.3f)))
            drawCircle(Color(0xFFFFC27A), 0.8f * u, Offset(X(216f), Y(24.8f)))
            drawCircle(PipoColors.blush, 0.8f * u, Offset(X(219f), Y(24.8f)))
        }

        // --- toys
        run {
            drawRect(Color(0xFFC79E5B), Offset(X(228f), Y(9f)), Size(12f * u, 9f * u))
            for (i in 0..2) drawLine(Color(0xFFE8C98A), Offset(X(228f + i * 4f + 1f), Y(9f)), Offset(X(228f + i * 4f + 1f), Y(0f)), 0.6f * u)
            val rocket = Path().apply { moveTo(X(235f), Y(18f)); lineTo(X(237f), Y(12f)); lineTo(X(233f), Y(12f)); close() }
            drawPath(rocket, Color(0xFFDDE3EB)); drawRect(Color(0xFFDDE3EB), Offset(X(233f), Y(12f)), Size(4f * u, 3.5f * u))
            if ("ball_on_bed" !in st.pranks) drawBall(Offset(X(st.ballU), fy + 1.5f * u), u)
        }
    }
}

private fun DrawScope.drawBall(c: Offset, u: Float) {
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(c.x - 3f * u, c.y + 2.2f * u), Size(6f * u, 1.6f * u))
    drawCircle(Color(0xFFE98A7A), 3f * u, c)
    drawLine(Color.White.copy(alpha = 0.8f), Offset(c.x - 3f * u, c.y), Offset(c.x + 3f * u, c.y), 0.6f * u)
    drawCircle(Color.White.copy(alpha = 0.5f), 0.8f * u, Offset(c.x - 1.2f * u, c.y - 1.2f * u))
}

/** Blanket drawn over Pipo when he's in bed. */
fun DrawScope.drawBlanket(g: SceneGeo, camU: Float, t: Float) {
    val u = g.u
    val x = (18f - camU) * u
    val y = g.floorY - 17.5f * u + sin(t * 1.1f) * 0.3f * u
    drawRoundRect(Color(0xFF6F8FA6), Offset(x, y), Size(30f * u, 9f * u), CornerRadius(3f * u))
    drawRoundRect(Color(0xFF86A5BA), Offset(x, y), Size(30f * u, 2.2f * u), CornerRadius(1.1f * u))
}

/** Night darkening + warm/cool light sources + vignette. */
fun DrawScope.drawLighting(g: SceneGeo, camU: Float, st: RoomState, pipoHead: Offset, glow: Color, t: Float) {
    val u = g.u
    val night = 1f - dayFactor(st.hour)
    if (night > 0.01f) drawRect(Color(0xFF0A0F2A).copy(alpha = 0.42f * night), Offset.Zero, size)
    fun glowAt(worldX: Float, heightU: Float, radiusU: Float, col: Color, a: Float) {
        if (a <= 0.01f) return
        val c = Offset((worldX - camU) * u, g.floorY - heightU * u)
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = a), Color.Transparent), center = c, radius = radiusU * u), radiusU * u, c, blendMode = BlendMode.Screen)
    }
    glowAt(150.5f, 30f, 42f, Color(0xFFFFB866), 0.5f * night)
    glowAt(84f, 2f, 18f, Color(0xFF7FE3F0), if (st.charging) 0.45f + 0.2f * abs(sin(t * 2.2f)) else 0.15f * night)
    glowAt(215f, 32f, 24f, PipoColors.eye, 0.3f * night)
    glowAt(137f, 28f, 20f, Color(0xFF9FE7E0), 0.25f * night)
    val pr = if (st.torch) 80f * u else 22f * u
    drawCircle(Brush.radialGradient(listOf((if (st.torch) Color(0xFFFFF4D6) else glow).copy(alpha = if (st.torch) 0.55f else 0.22f * night), Color.Transparent),
        center = pipoHead, radius = pr), pr, pipoHead, blendMode = BlendMode.Screen)
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), center = Offset(size.width / 2, size.height * 0.5f),
        radius = size.maxDimension * 0.75f), Offset.Zero, size)
}

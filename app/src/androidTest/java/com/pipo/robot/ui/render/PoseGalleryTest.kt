package com.pipo.robot.ui.render

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pipo.robot.data.ItemShape
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.Expr
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders Pipo with the app's real painter on the device's real Canvas and writes image sheets
 * to the app's external files dir (`pose_gallery/`), so angles, poses, expressions and the room
 * at day/night can be inspected visually. Pull with:
 * `adb pull /sdcard/Android/data/com.pipo.robot/files/pose_gallery`
 *
 * It also asserts the renders aren't blank (Pipo actually drew pixels in every cell).
 */
@RunWith(AndroidJUnit4::class)
class PoseGalleryTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val outDir = File(ctx.getExternalFilesDir(null), "pose_gallery").apply { mkdirs() }
    private val bg = Color(0xFF7F97A3)

    private class Cell(val label: String, val setup: (PipoRig) -> Unit)

    private fun settle(rig: PipoRig, anim: AnimState, expr: Expr, secs: Float = 1.6f) {
        rig.fidgetsEnabled = false
        rig.anim = anim; rig.expr = expr
        var t = 0f
        while (t < secs) { rig.update(1f / 60f); t += 1f / 60f }
    }

    private fun sheet(name: String, cells: List<Cell>, cols: Int = 4, cw: Int = 300, ch: Int = 340, light: PipoLight = PipoLight()): File {
        val rows = (cells.size + cols - 1) / cols
        val img = ImageBitmap(cols * cw, rows * ch)
        val canvas = Canvas(img)
        val nonBlank = BooleanArray(cells.size)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(cols * cw.toFloat(), rows * ch.toFloat())) {
            drawRect(Color(0xFF2B3342))
            cells.forEachIndexed { i, c ->
                val x = (i % cols) * cw.toFloat(); val y = (i / cols) * ch.toFloat()
                drawRect(bg, topLeft = androidx.compose.ui.geometry.Offset(x + 2, y + 2), size = Size(cw - 4f, ch - 4f))
                val rig = PipoRig(i + 1)
                c.setup(rig)
                drawPipo(rig, x + cw / 2f, y + ch * 0.86f, ch * 0.62f, light = light)
            }
        }
        val bmp = img.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
        val ac = android.graphics.Canvas(bmp)
        val p = Paint().apply { color = android.graphics.Color.WHITE; textSize = 22f; isAntiAlias = true }
        cells.forEachIndexed { i, c ->
            val x = (i % cols) * cw; val y = (i / cols) * ch
            ac.drawText(c.label, x + 10f, y + 30f, p)
            // any pixel noticeably different from the background inside the cell → Pipo was drawn
            val bgArgb = android.graphics.Color.argb(255, 0x7F, 0x97, 0xA3)
            var diff = 0
            var yy = y + 50
            while (yy < y + ch - 10) { var xx = x + 10; while (xx < x + cw - 10) { if (bmp.getPixel(xx, yy) != bgArgb) diff++; xx += 6 }; yy += 6 }
            nonBlank[i] = diff > 40
        }
        val f = File(outDir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cells.forEachIndexed { i, c -> assertTrue("${name}: '${c.label}' rendered nothing", nonBlank[i]) }
        return f
    }

    private fun yawCell(label: String, yaw: Float, anim: AnimState = AnimState.IDLE, expr: Expr = Expr.CONTENT) = Cell(label) { r ->
        settle(r, anim, expr); r.yaw = yaw; r.headYaw = yaw
    }

    @Test
    fun turntable() {
        sheet("01_turntable", listOf(
            yawCell("front 0", 0f), yawCell("3/4 0.6", 0.6f), yawCell("3/4 1.0", 1.0f), yawCell("side 1.57", 1.5708f),
            yawCell("back-3/4 2.3", 2.3f), yawCell("back 3.14", 3.1416f), yawCell("3/4 -0.6", -0.6f), yawCell("side -1.57", -1.5708f),
            Cell("head leads body") { r -> settle(r, AnimState.IDLE, Expr.CURIOUS); r.yaw = 0.3f; r.headYaw = 0.9f },
            Cell("walk right") { r -> r.moveDir = 1f; settle(r, AnimState.WALKING, Expr.CONTENT) },
            Cell("walk left") { r -> r.moveDir = -1f; settle(r, AnimState.WALKING, Expr.CONTENT) },
            Cell("run right") { r -> r.moveDir = 1f; settle(r, AnimState.RUNNING, Expr.EXCITED) },
        ))
    }

    @Test
    fun poses() {
        val list = listOf(
            AnimState.SULK, AnimState.TURN_AWAY, AnimState.ARMS_CROSSED, AnimState.EMBARRASSED,
            AnimState.LIE_DOWN, AnimState.SITTING, AnimState.SNEAK, AnimState.DANCING,
            AnimState.CELEBRATE, AnimState.YAWN, AnimState.DIZZY, AnimState.LAUGH,
            AnimState.FALLEN, AnimState.HIDING, AnimState.THINKING, AnimState.FINGER_UP,
            AnimState.READING, AnimState.PRESENTING, AnimState.WAVE, AnimState.PEEK,
            AnimState.PHONE, AnimState.GAMING, AnimState.SITTING, AnimState.STRETCH,
        )
        sheet("02_poses", list.map { a ->
            Cell(a.name.lowercase()) { r ->
                if (a == AnimState.PRESENTING) r.holdItem = ItemShape.GEAR
                settle(r, a, when (a) { AnimState.DIZZY -> Expr.DIZZY; AnimState.LAUGH -> Expr.LAUGH; AnimState.SULK, AnimState.ARMS_CROSSED, AnimState.TURN_AWAY -> Expr.ANNOYED; AnimState.YAWN -> Expr.SLEEPY; else -> Expr.CONTENT }, if (a == AnimState.YAWN) 1.1f else 1.6f)
            }
        })
    }

    @Test
    fun getUpSequence() {
        sheet("03_get_up", (0..7).map { i ->
            Cell("t=${"%.2f".format(i * 0.18f)}") { r ->
                settle(r, AnimState.LIE_DOWN, Expr.CONTENT, 1.5f)
                r.anim = AnimState.GET_UP
                var t = 0f; while (t < i * 0.18f) { r.update(1f / 60f); t += 1f / 60f }
            }
        })
    }

    @Test
    fun expressions() {
        sheet("04_expressions", Expr.values().map { e -> Cell(e.name.lowercase()) { r -> settle(r, AnimState.IDLE, e) } }, cols = 6, cw = 240, ch = 280)
    }

    @Test
    fun lighting() {
        val night = RoomState(hour = 23.5f)
        val cells = listOf(100f, 135f, 150f, 165f, 200f).map { x -> Cell("night x=$x") { r -> settle(r, AnimState.IDLE, Expr.CONTENT) } }
        // one sheet per light position so each gets its own light
        listOf(100f, 135f, 150f, 165f, 200f).forEachIndexed { i, x ->
            sheet("05_light_night_x${x.toInt()}", listOf(cells[i]), cols = 1, light = pipoLight(night, x, Color(0xFF8FF5E2), torch = false))
        }
        sheet("05_light_day_x60", listOf(Cell("day x=60") { r -> settle(r, AnimState.IDLE, Expr.CONTENT) }), cols = 1, light = pipoLight(RoomState(hour = 13f), 60f, Color(0xFF8FF5E2), false))
    }

    /** Whole room at a given hour, camera at [camU], Pipo standing at [pipoX]. */
    private fun room(name: String, hour: Float, camU: Float, pipoX: Float, anim: AnimState = AnimState.IDLE, console: Boolean = false) {
        val w = 1080; val h = 2400
        val img = ImageBitmap(w, h)
        val g = SceneGeo(w.toFloat(), h.toFloat())
        val st = RoomState(hour = hour, drawings = 2, charging = true, battery = 64, computerActive = true, benchActive = true, plantRustle = 0.6f,
            pranks = setOf("plant_hat", "clock_sideways"), consoleActive = console)
        val rig = PipoRig(9)
        settle(rig, anim, Expr.CONTENT)
        CanvasDrawScope().draw(Density(2.625f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            val t = 20f
            drawRoom(g, camU, st, t, pipoInBed = false)
            val fx = g.sx(pipoX, camU)
            drawPipo(rig, fx, g.pipoFootY, g.pipoH, light = pipoLight(st, pipoX, Color(rig.glow), false))
            drawLighting(g, camU, st, androidx.compose.ui.geometry.Offset(fx, g.pipoFootY - g.pipoH), Color(rig.glow), t)
            drawForeground(g, camU, st)
        }
        File(outDir, "$name.png").outputStream().use { img.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    @Test
    fun rooms() {
        room("06_room_day_window", 13f, 60f, 104f)
        room("06_room_evening_window", 18.8f, 60f, 104f)
        room("06_room_night_desk", 23.5f, 100f, 145f)
        room("06_room_night_workshop", 23.5f, 150f, 185f)
        room("06_room_day_bed", 9f, 0f, 70f, AnimState.CHEERFUL)
        room("07_room_night_console", 21.5f, 55f, 92f, AnimState.GAMING, console = true)
        room("07_room_day_phone", 15f, 40f, 80f, AnimState.PHONE)
    }
}

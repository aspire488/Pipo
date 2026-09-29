package com.pipo.robot.ui.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorMathTest {
    @Test
    fun endpointsAndMidpoint() {
        val a = Color(0xFF102030); val b = Color(0x80F0E0D0)
        assertEquals(a.toArgb(), lerp(a, b, 0f).toArgb())
        assertEquals(b.toArgb(), lerp(a, b, 1f).toArgb())
        assertEquals(Color(0xC0808080).toArgb(), lerp(a, b, 0.5f).toArgb())
    }

    @Test
    fun fractionIsClamped() {
        val a = Color.Black; val b = Color.White
        assertEquals(a.toArgb(), lerp(a, b, -3f).toArgb())
        assertEquals(b.toArgb(), lerp(a, b, 7f).toArgb())
    }
}

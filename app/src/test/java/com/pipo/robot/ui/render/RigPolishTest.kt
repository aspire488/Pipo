package com.pipo.robot.ui.render

import com.pipo.robot.data.Mood
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.Expr
import org.junit.Assert.assertTrue
import org.junit.Test

class RigPolishTest {

    private fun settled(expr: Expr = Expr.CONTENT) = PipoRig(1).apply { this.expr = expr; repeat(120) { update(1f / 60f) } }

    @Test
    fun aNewFeelingShowsInHisEyesBeforeHisMouth() {
        val r = settled()
        r.expr = Expr.SURPRISED // round eyes, open mouth
        repeat(5) { r.update(1f / 60f) }
        val eyes = r.face.round / 1f
        val mouth = r.face.mouthOpen / 0.8f
        assertTrue("eyes $eyes should be ahead of mouth $mouth", eyes > mouth)
    }

    @Test
    fun hummingClosesHisMouthAndSwaysHim() {
        val r = settled()
        r.hum(2f)
        repeat(60) { r.update(1f / 60f) }
        assertTrue(r.humming)
        assertTrue("mouth closed while humming (${r.face.mouthOpen})", r.face.mouthOpen < 0.1f)
        repeat(120) { r.update(1f / 60f) }
        assertTrue(!r.humming)
    }

    @Test
    fun heDipsBeforeSettingOff() {
        val r = settled()
        r.anticipate()
        var maxCrouch = 0f
        repeat(15) { r.update(1f / 60f); maxCrouch = maxOf(maxCrouch, r.pose.crouch) }
        assertTrue("anticipation crouch ($maxCrouch)", maxCrouch > 0.03f)
    }

    @Test
    fun sittingCompressesHisBody() {
        val stand = settled().apply { anim = AnimState.IDLE; repeat(120) { update(1f / 60f) } }
        val sit = settled().apply { anim = AnimState.SITTING; repeat(120) { update(1f / 60f) } }
        assertTrue(sit.pose.squash < stand.pose.squash)
    }

    /** Every mood's idle, with every micro-fidget it can pick, over a long stretch: never NaN, never wild. */
    @Test
    fun everyMoodsIdleStaysFiniteAndInBounds() {
        for (m in Mood.entries) {
            val r = PipoRig(m.ordinal + 3)
            r.mood = m; r.anim = AnimState.IDLE; r.energy = 0.3f
            repeat(60 * 40) {
                r.update(1f / 60f)
                val p = r.pose
                for (v in listOf(p.lean, p.headTilt, p.squash, p.armL, p.armR, p.crouch, r.yaw, r.face.open, r.face.mouthOpen, r.antenna))
                    assertTrue("$m: $v", v.isFinite())
                assertTrue("$m squash ${p.squash}", p.squash in 0.7f..1.3f)
            }
        }
    }

    @Test
    fun pokesAreLocalReactions() {
        val r = settled()
        val before = r.antenna
        r.poked(PipoRig.PokeSpot.HEAD)
        repeat(6) { r.update(1f / 60f) }
        assertTrue("the antenna boings when you poke his head", kotlin.math.abs(r.antenna - before) > 1f)
    }
}

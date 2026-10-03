package com.pipo.robot.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The downloaded model is untrusted input until proven otherwise: checksum and zip-slip guards. */
class WakeModelTest {
    private val root: File = Files.createTempDirectory("wm").toFile()

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> entries.forEach { (n, body) -> z.putNextEntry(ZipEntry(n)); z.write(body.toByteArray()); z.closeEntry() } }
        return bos.toByteArray()
    }

    @Test
    fun unpacksUnderTheRootWithoutTheTopFolder() {
        WakeModel.unzip(zip("vosk-model/am/final.mdl" to "x", "vosk-model/conf/model.conf" to "y").inputStream(), root)
        assertEquals("x", File(root, "am/final.mdl").readText())
        assertEquals("y", File(root, "conf/model.conf").readText())
    }

    @Test
    fun refusesEntriesThatEscapeTheRoot() {
        assertNull(WakeModel.entryTarget(root, "top/../../evil.so"))
        assertNull(WakeModel.entryTarget(root, "top/"))
        try {
            WakeModel.unzip(zip("top/../../evil.so" to "boom").inputStream(), root)
            fail("a path-escaping entry must abort the unpack")
        } catch (_: SecurityException) { }
        assertFalse(File(root.parentFile, "evil.so").exists())
    }

    @Test
    fun checksumIsTheRealSha256() {
        val f = File(root, "f").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", WakeModel.sha256(f))
        assertTrue(WakeModel.SHA256.length == 64)
    }
}

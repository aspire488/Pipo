package com.pipo.robot.data

import com.pipo.robot.ui.render.SceneGeo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The Unity world is built from the app's own catalog, never a hand-copied list. This writes
 * unity/PipoWorld/Assets/Resources/world.json (when PIPO_EXPORT_WORLD=1) and otherwise
 * fails if Unity's copy has drifted from the catalog, so no place can silently go missing.
 */
class WorldExportTest {
    private fun strings(xs: Collection<String>) = JsonArray(xs.map { JsonPrimitive(it) })

    private fun export(): String {
        val root = buildJsonObject {
            put("v", 1)
            put("home", buildJsonObject {
                put("id", "home"); put("name", "Pipo's room"); put("kind", "HOME"); put("width", SceneGeo.WORLD_W)
                put("objects", buildJsonArray {
                    for (o in SceneGeo.objects.distinctBy { it.id }) add(buildJsonObject { put("id", o.id); put("l", o.l); put("r", o.r); put("bottom", o.bottom); put("top", o.top) })
                })
                put("stations", buildJsonObject { for (s in Station.entries) put(s.name, SceneGeo.station(s)) })
            })
            put("places", buildJsonArray {
                for (p in Places.all) add(buildJsonObject {
                    put("id", p.id); put("name", p.name); put("kind", p.kind.name); put("travelMin", p.travelMin)
                    put("opens", p.opens); put("closes", p.closes); put("npc", p.npc)
                    put("sells", strings(p.sells)); put("wildlife", strings(p.wildlife))
                    put("mapX", p.mapX); put("mapY", p.mapY); put("color", "#%06X".format(p.color and 0xFFFFFF)); put("blurb", p.blurb)
                })
            })
            put("npcs", buildJsonArray { for (n in Npcs.all) add(buildJsonObject { put("id", n.id); put("name", n.name); put("placeId", n.placeId); put("hello", n.hello) }) })
            put("species", buildJsonArray { for (s in Wildlife.species) add(buildJsonObject { put("id", s.id); put("name", s.name); put("daytime", s.daytime.name) }) })
        }
        return Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root) + "\n"
    }

    @Test
    fun unityWorldMatchesTheAppCatalog() {
        val file = File("../unity/PipoWorld/Assets/Resources/world.json")
        val json = export()
        if (System.getenv("PIPO_EXPORT_WORLD") == "1") { file.parentFile.mkdirs(); file.writeText(json); return }
        if (!file.exists()) return   // no Unity project checked out: nothing to drift
        assertEquals("Unity's world.json is stale: run the tests with PIPO_EXPORT_WORLD=1", json, file.readText().replace("\r\n", "\n"))
    }
}

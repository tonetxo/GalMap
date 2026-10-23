package com.tonetxo.galmap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverpassParserTest {

    @Test
    fun `parses nodes and way or relation centres`() {
        val body = """
            {"elements":[
              {"type":"node","id":1,"lat":42.8,"lon":-8.4,"tags":{"natural":"peak","name":"Pico Sacro","ele":"533"}},
              {"type":"relation","id":2,"center":{"lat":42.38,"lon":-8.92},"tags":{"place":"island","name":"Isla de Ons","name:gl":"Illa de Ons"}},
              {"type":"node","id":3,"lat":43.0,"lon":-9.3,"tags":{"natural":"cape","name":"Cabo Fisterra"}},
              {"type":"node","id":4,"lat":42.0,"lon":-8.0,"tags":{"natural":"peak"}},
              {"type":"node","id":5,"lat":42.1,"lon":-8.1,"tags":{"natural":"tree","name":"Carballo"}}
            ]}
        """.trimIndent()

        val features = OverpassParser.parse(body)

        assertEquals(3, features.size)
        val (peak, island, cape) = features
        assertEquals(FeatureType.ISLAND, island.type)
        assertEquals("Pico Sacro", peak.name)
        assertEquals(FeatureType.PEAK, peak.type)
        assertEquals(533.0, peak.elevationM!!, 1e-9)
        assertEquals("Illa de Ons", island.name)
        assertEquals("relation/2", island.id)
        assertEquals(42.38, island.lat, 1e-9)
        assertEquals(0.0, island.elevationM!!, 1e-9)
        assertEquals(FeatureType.CAPE, cape.type)
    }

    @Test
    fun `peaks without ele keep unknown elevation`() {
        val body = """{"elements":[{"type":"node","id":1,"lat":42.0,"lon":-8.0,"tags":{"natural":"peak","name":"Monte"}}]}"""
        assertNull(OverpassParser.parse(body).single().elevationM)
    }

    @Test
    fun `elevation parsing tolerates units and decimal commas`() {
        assertEquals(1234.0, OverpassParser.parseElevation("1234")!!, 1e-9)
        assertEquals(1234.0, OverpassParser.parseElevation("1234 m")!!, 1e-9)
        assertEquals(560.5, OverpassParser.parseElevation("560,5")!!, 1e-9)
        assertEquals(457.2, OverpassParser.parseElevation("1500 ft")!!, 1e-6)
        assertNull(OverpassParser.parseElevation("unknown"))
        assertNull(OverpassParser.parseElevation(null))
    }
}

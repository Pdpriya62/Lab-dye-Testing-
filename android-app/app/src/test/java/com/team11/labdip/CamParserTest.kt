package com.team11.labdip

import com.team11.labdip.data.CameraRepository
import com.team11.labdip.data.model.CamParser
import com.team11.labdip.data.mqtt.camTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CamParserTest {
    // Shape of what cam_capture.py --mqtt publishes (3x3 grid, target and rig reading known)
    private val cells = """[[82.4,-3.1,15.2,0.71],null,[81.9,-2.8,14.1,0.6],[82.0,-3.0,14.5,0.2],[82.1,-2.9,14.6,0.1],
        [80.2,-2.5,16.9,2.57],[82.3,-3.0,14.4,0.3],[82.2,-3.1,14.7,0.4],[82.0,-2.9,14.5,0.2]]"""
    private val valid = """{"sample_id":"S7","timestamp":"2026-11-12T14:51:36","L":82.13,"a":-2.95,"b":14.58,"R":210,"G":205,
        "B":177,"grid":3,"cells":$cells,"roi":"auto","white":"flat","levelness_max":2.57,"levelness_mean":1.18,"level_tol":1.0,
        "uniform":false,"target_L":82.0,"target_a":-3.0,"target_b":15.0,"deltaE2000":0.27,"sensor_L":80.5,"sensor_a":-3.1,
        "sensor_b":18.2,"cam_vs_sensor_dE2000":2.38}"""
    private val minimal = """{"sample_id":"S1","timestamp":"t","L":50,"a":0,"b":0,"R":1,"G":2,"B":3,"grid":2,
        "cells":[null,null,null,null],"roi":"centre","white":"none"}"""

    private fun parse(s: String) = CamParser.parse(s.encodeToByteArray())
    private fun rejected(s: String) = assertTrue("should be rejected: $s", parse(s).isFailure)

    @Test fun validPayload() {
        val c = parse(valid).getOrThrow()
        assertEquals("S7", c.sampleId)
        assertEquals(9, c.cells.size)
        assertNull(c.cells[1])
        assertEquals(2.57, c.cells[5]!![3], 1e-9)
        assertEquals(0.27, c.deltaE2000!!, 1e-9)
        assertEquals(false, c.uniform)
        assertTrue(c.hasTarget)
    }

    @Test fun minimalPayloadWithoutOptionalFields() {
        val c = parse(minimal).getOrThrow()
        assertNull(c.deltaE2000)
        assertNull(c.levelnessMax)
        assertTrue(!c.hasTarget)
    }

    @Test fun oversizeRejected() = rejected(valid.replace("\"S7\"", "\"S7\",\"pad\":\"" + "x".repeat(2100) + "\""))

    @Test fun gridAndCellShapeChecked() {
        rejected(minimal.replace("\"grid\":2", "\"grid\":5"))
        rejected(minimal.replace("[null,null,null,null]", "[null,null,null]"))
        rejected(minimal.replace("[null,null,null,null]", "[null,null,null,[50,0,0]]"))
        rejected(minimal.replace("[null,null,null,null]", "[null,null,null,[50,0,0,400]]"))
    }

    @Test fun rangesChecked() {
        rejected(minimal.replace("\"L\":50", "\"L\":120"))
        rejected(minimal.replace("\"a\":0", "\"a\":-200"))
        rejected(minimal.replace("\"R\":1", "\"R\":300"))
        rejected(minimal.replace("\"L\":50", "\"L\":\"NaN\""))
        rejected(valid.replace("\"deltaE2000\":0.27", "\"deltaE2000\":-1"))
    }

    @Test fun enumsChecked() {
        rejected(minimal.replace("\"roi\":\"centre\"", "\"roi\":\"x\""))
        rejected(minimal.replace("\"white\":\"none\"", "\"white\":\"rainbow\""))
    }

    @Test fun partialTargetRejected() = rejected(minimal.replace("\"grid\"", "\"target_L\":50,\"grid\""))

    @Test fun unsafeSampleIdBecomesQuestionMark() =
        assertEquals("?", parse(minimal.replace("\"S1\"", "\"S1;rm\"")).getOrThrow().sampleId)

    @Test fun notJsonRejected() {
        rejected("not json")
        rejected("[1,2]")
        rejected("")
    }

    @Test fun topicSitsNextToDataTopic() = assertEquals("rvu/team11/labdip/cam", camTopic("rvu/team11/labdip/data"))

    @Test fun repositoryKeepsLatestAndCountsDrops() {
        val repo = CameraRepository { 42L }
        repo.ingest("junk".encodeToByteArray())
        assertNull(repo.latest.value)
        assertEquals(1, repo.dropped.value)
        repo.ingest(valid.encodeToByteArray())
        assertEquals("S7", repo.latest.value!!.result.sampleId)
        assertEquals(42L, repo.latest.value!!.receivedAt)
    }
}

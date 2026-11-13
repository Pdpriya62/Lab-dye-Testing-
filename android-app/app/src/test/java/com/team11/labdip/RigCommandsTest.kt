package com.team11.labdip

import com.team11.labdip.data.mqtt.RigCommands
import com.team11.labdip.data.mqtt.ackTopic
import com.team11.labdip.data.mqtt.commandTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RigCommandsTest {
    @Test fun topicsSitNextToDataTopic() {
        assertEquals("rvu/team11/labdip/cmd", commandTopic("rvu/team11/labdip/data"))
        assertEquals("rvu/team11/labdip/ack", ackTopic("rvu/team11/labdip/data"))
        assertEquals("cmd", commandTopic("labdip"))
    }

    @Test fun validCommandsMatchSerialSyntax() {
        assertEquals("ID S002", RigCommands.id(" S002 ").getOrThrow())
        assertEquals("TARGET 52.30 10.10 -4.20", RigCommands.target("52.3", "10.1", "-4.2").getOrThrow())
        assertEquals("BATH 60.00", RigCommands.bath("60").getOrThrow())
        assertEquals("CONC 2.00", RigCommands.conc("2").getOrThrow())
        assertEquals("DYE reactive red", RigCommands.dye("reactive red").getOrThrow())
        assertEquals("CAPTURE", RigCommands.capture().getOrThrow())
        assertEquals("PAUSE", RigCommands.pause().getOrThrow())
        assertEquals("RESUME", RigCommands.resume().getOrThrow())
    }

    @Test fun rejectsOutOfRangeAndUnsafeInput() {
        assertTrue(RigCommands.target("101", "0", "0").isFailure)
        assertTrue(RigCommands.target("50", "x", "0").isFailure)
        assertTrue(RigCommands.bath("131").isFailure)
        assertTrue(RigCommands.bath("NaN").isFailure)
        assertTrue(RigCommands.conc("-1").isFailure)
        assertTrue(RigCommands.id("").isFailure)
        assertTrue(RigCommands.id("S1;BATH 99").isFailure)
        assertTrue(RigCommands.fabric("a".repeat(25)).isFailure)
    }

    @Test fun repliesAreShortPrintableAscii() {
        assertEquals("OK sample=S002", RigCommands.cleanReply("OK sample=S002".toByteArray()))
        assertNull(RigCommands.cleanReply(ByteArray(0)))
        assertNull(RigCommands.cleanReply("x".repeat(129).toByteArray()))
        assertNull(RigCommands.cleanReply("OK\nfake".toByteArray()))
    }
}

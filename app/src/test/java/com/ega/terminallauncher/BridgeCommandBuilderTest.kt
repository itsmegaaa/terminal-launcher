package com.ega.terminallauncher

import com.ega.terminallauncher.bridge.BridgeCommandBuilder
import com.ega.terminallauncher.bridge.TermuxBridgeConstants
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeCommandBuilderTest {

    @Test
    fun testBuildTimeoutArguments_preservesQuotesAndSpecialCharacters() {
        val complexCmd = "echo \"a'b \$HOME \\n c\" | grep 'b' && ls -la `pwd` ◈"
        val timeoutSec = 30

        val args = BridgeCommandBuilder.buildTimeoutArguments(complexCmd, timeoutSec)

        val expected = arrayOf(
            "-k", "2",
            "30",
            TermuxBridgeConstants.DEFAULT_BASH_PATH,
            "-c",
            complexCmd
        )

        assertArrayEquals(expected, args)
        assertEquals(complexCmd, args[5])
    }

    @Test
    fun testBuildTimeoutArguments_customTimeoutParameter() {
        val cmd = "sleep 60"
        val timeoutSec = 5

        val args = BridgeCommandBuilder.buildTimeoutArguments(cmd, timeoutSec)

        assertEquals("5", args[2])
        assertEquals(cmd, args[5])
    }
}

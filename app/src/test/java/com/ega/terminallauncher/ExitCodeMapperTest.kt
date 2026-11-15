package com.ega.terminallauncher

import com.ega.terminallauncher.bridge.ExitCodeMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class ExitCodeMapperTest {

    @Test
    fun testExitCode0_returnsSuccess() {
        val msg = ExitCodeMapper.mapExitCode(exitCode = 0, durationMs = 120, timeoutSec = 30)
        assertEquals("Success", msg)
    }

    @Test
    fun testExitCode124_returnsTimeoutMessage() {
        val msg = ExitCodeMapper.mapExitCode(exitCode = 124, durationMs = 5100, timeoutSec = 5)
        assertEquals("[Command timed out after 5s]", msg)
    }

    @Test
    fun testExitCode137_killedAfterTimeout() {
        val msg = ExitCodeMapper.mapExitCode(exitCode = 137, durationMs = 7050, timeoutSec = 5)
        assertEquals("[Command force-killed after timeout]", msg)
    }

    @Test
    fun testExitCode137_killedBySystemOOM() {
        val msg = ExitCodeMapper.mapExitCode(exitCode = 137, durationMs = 1200, timeoutSec = 30)
        assertEquals("[Command killed by system / OOM]", msg)
    }

    @Test
    fun testNonZeroExitCode_returnsGeneralError() {
        val msg = ExitCodeMapper.mapExitCode(exitCode = 2, durationMs = 50, timeoutSec = 30)
        assertEquals("[Process exited with code 2]", msg)
    }
}

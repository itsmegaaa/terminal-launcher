package com.ega.terminallauncher

import com.ega.terminallauncher.bridge.ExecutionIdManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ExecutionIdManagerTest {

    @Test
    fun testRequestCodeUniqueness_underConcurrentCalls() {
        val pool = Executors.newFixedThreadPool(16)
        val generatedCodes = ConcurrentHashMap.newKeySet<Int>()
        val totalCalls = 1000

        for (i in 0 until totalCalls) {
            pool.submit {
                val code = ExecutionIdManager.nextRequestCode()
                generatedCodes.add(code)
            }
        }

        pool.shutdown()
        pool.awaitTermination(5, TimeUnit.SECONDS)

        assertEquals(totalCalls, generatedCodes.size)
    }
}

package com.pumpwatch.app.testing

import com.pumpwatch.app.data.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ConcurrencyTest {

    @Test
    fun `SingleFlight prevents concurrent execution of same request`() = runBlocking {
        val singleFlight = ApiClient.SingleFlight<String>()
        val executionCount = AtomicInteger(0)

        val results = (1..5).map {
            async(Dispatchers.Default) {
                singleFlight.joinOrRun {
                    executionCount.incrementAndGet()
                    delay(100)
                    "SuccessResult"
                }
            }
        }.awaitAll()

        assertEquals(5, results.size)
        assertTrue("All results must be identical", results.all { it == "SuccessResult" })
        assertEquals("Block should execute exactly once", 1, executionCount.get())
    }

    @Test
    fun `SingleFlight propagates exceptions to all waiting coroutines`() = runBlocking {
        val singleFlight = ApiClient.SingleFlight<String>()
        val executionCount = AtomicInteger(0)

        val results = (1..3).map {
            async(Dispatchers.Default) {
                runCatching {
                    singleFlight.joinOrRun {
                        executionCount.incrementAndGet()
                        delay(50)
                        throw RuntimeException("NetworkError")
                    }
                }
            }
        }.awaitAll()

        assertEquals("Block should execute exactly once even on failure", 1, executionCount.get())
        assertTrue("All results must be failures", results.all { it.isFailure })
        assertTrue(
            "All exceptions must have the same message",
            results.all { it.exceptionOrNull()?.message == "NetworkError" }
        )
    }

    @Test
    fun `SingleFlight allows sequential requests after completion`() = runBlocking {
        val singleFlight = ApiClient.SingleFlight<String>()
        val executionCount = AtomicInteger(0)

        val first = singleFlight.joinOrRun {
            executionCount.incrementAndGet()
            delay(50)
            "First"
        }

        val second = singleFlight.joinOrRun {
            executionCount.incrementAndGet()
            delay(50)
            "Second"
        }

        assertEquals("First", first)
        assertEquals("Second", second)
        assertEquals("Both sequential requests should execute", 2, executionCount.get())
    }
}

package moodle.mcp

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import moodle.fixtures.McpFixture
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Timeout(15)
class McpConcurrencyTest {
    @Test
    fun `four calls can run together and a cancelled queued call never reaches the service`() = runBlocking {
        val fixture = McpFixture()
        val entered = CountDownLatch(4)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        fixture.beforeCourses = {
            peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            entered.countDown()
            try {
                check(release.await(5, TimeUnit.SECONDS))
            } finally {
                active.decrementAndGet()
            }
        }
        val calls = List(4) { async { fixture.tools.call("list_courses", buildJsonObject {}) } }
        try {
            // Yield the runBlocking event loop while its children dispatch their blocking work.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
            }
            val queued = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.tools.call("list_courses", buildJsonObject {})
            }
            queued.cancelAndJoin()
        } finally {
            release.countDown()
        }
        withTimeout(5000) { calls.awaitAll().forEach { assertEquals(false, it.isError) } }
        assertEquals(4, peak.get())
        assertEquals(4, fixture.courseCalls)
        assertEquals(false, fixture.tools.call("list_courses", buildJsonObject {}).isError)
    }

    @Test
    fun `cancelling active interruptible work releases its permit`() = runBlocking {
        val fixture = McpFixture()
        val entered = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        fixture.beforeCourses = {
            entered.countDown()
            try {
                CountDownLatch(1).await()
            } finally {
                interrupted.countDown()
            }
        }
        val call = async { fixture.tools.call("list_courses", buildJsonObject {}) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
        }
        withTimeout(5000) { call.cancelAndJoin() }
        assertTrue(interrupted.await(1, TimeUnit.SECONDS))
        fixture.beforeCourses = {}
        assertEquals(false, fixture.tools.call("list_courses", buildJsonObject {}).isError)
    }
}

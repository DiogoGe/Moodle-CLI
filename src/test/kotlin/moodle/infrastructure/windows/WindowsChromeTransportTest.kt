package moodle.infrastructure.windows

import moodle.fixtures.FakeWindowsKernel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsChromeTransportTest {
    @Test
    fun `browser inherits only protocol handles and enters its cleanup job before it starts`() {
        val kernel = FakeWindowsKernel()
        val browser = WindowsChromeTransport.start(
            listOf("C:\\Program Files\\Chrome\\chrome.exe", "--remote-debugging-pipe"),
            kernel.native
        )
        assertTrue(browser.isAlive)
        assertEquals(listOf(10L, 13L, 15L), kernel.inheritedHandles)
        assertEquals(listOf(11L, 12L), kernel.protectedHandles)
        assertTrue(kernel.command.contains("--remote-debugging-io-pipes=10,13"))
        assertFalse(kernel.command.contains("--remote-debugging-port"))
        assertEquals(0x80404, kernel.creationFlags)
        assertEquals(WindowsStartupInfo().size(), kernel.startupSize)
        assertEquals(0x2000, kernel.jobFlags)
        assertEquals(0x100, kernel.consoleFlags)
        assertEquals(listOf(15L, 15L, 15L), kernel.consoleHandles)
        assertTrue(kernel.calls.indexOf("AssignProcessToJobObject") < kernel.calls.indexOf("ResumeThread"))
        browser.close()
        browser.close()
        assertEquals((10L..17L).toSet(), kernel.closed.toSet())
        assertEquals(kernel.closed.size, kernel.closed.toSet().size)
        assertTrue(kernel.calls.indexOf("TerminateJobObject") < kernel.calls.indexOf("QueryInformationJobObject"))
    }

    @Test
    fun `failed browser creation releases pipe handles job and native attribute list`() {
        val kernel = FakeWindowsKernel().apply { failsAt = "CreateProcessW" }
        assertFailsWith<IllegalStateException> { WindowsChromeTransport.start(listOf("chrome.exe"), kernel.native) }
        assertEquals((10L..15L).toSet(), kernel.closed.toSet())
        assertTrue("DeleteProcThreadAttributeList" in kernel.calls)
    }

    @Test
    fun `job assignment failure terminates the suspended browser and closes all owned handles`() {
        val kernel = FakeWindowsKernel().apply { failsAt = "AssignProcessToJobObject" }
        assertFailsWith<IllegalStateException> { WindowsChromeTransport.start(listOf("chrome.exe"), kernel.native) }
        assertTrue("TerminateProcess" in kernel.calls)
        assertFalse("ResumeThread" in kernel.calls)
        assertEquals((10L..17L).toSet(), kernel.closed.toSet())
    }

    @Test
    fun `Windows environment removes browser log override and keeps double null termination`() {
        windowsBrowserEnvironment(mapOf("chrome_log_file" to "private.log", "SystemRoot" to "C:\\Windows")).use { memory ->
            val result = memory.getByteArray(0, memory.size().toInt()).toString(Charsets.UTF_16LE)
            assertEquals("SystemRoot=C:\\Windows\u0000\u0000", result)
        }
    }
}

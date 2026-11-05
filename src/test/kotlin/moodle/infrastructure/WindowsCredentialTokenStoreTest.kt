package moodle.infrastructure

import moodle.domain.AccessToken
import moodle.domain.exceptions.MoodleException
import moodle.fixtures.FakeWindowsCredentials
import moodle.infrastructure.credentials.MacKeychainTokenStore
import moodle.infrastructure.credentials.WindowsCredentialTokenStore
import moodle.infrastructure.credentials.platformTokenStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsCredentialTokenStoreTest {
    private val native = FakeWindowsCredentials()
    private val store = WindowsCredentialTokenStore("fixture", { native }, { native.errorCode })
    private val site = "https://fixture.invalid/"

    @Test
    fun `generic credential saves updates restores and deletes only the site target`() {
        assertNull(store.load(site))
        store.save(site, AccessToken("a".repeat(32)))
        assertEquals("fixture:https://fixture.invalid", native.target)
        assertEquals(2, native.savedPersistence)
        assertEquals("a".repeat(32), store.load(site)?.value)
        assertTrue(native.freed)
        assertTrue(native.clearedBeforeFree)
        store.save(site, AccessToken("b".repeat(32)))
        assertEquals("b".repeat(32), store.load(site)?.value)
        store.delete(site)
        assertNull(store.load(site))
        store.delete(site)
    }

    @Test
    fun `access errors remain errors instead of appearing signed out`() {
        native.errorCode = 5
        assertEquals("error.credentials.read", assertFailsWith<MoodleException.Credentials> { store.load(site) }.messageKey)
        assertEquals("error.credentials.delete", assertFailsWith<MoodleException.Credentials> { store.delete(site) }.messageKey)
        native.fails = true
        assertFailsWith<MoodleException.Credentials> { store.save(site, AccessToken("c".repeat(32))) }
    }

    @Test
    fun `invalid stored token is rejected and native buffer is freed and cleared`() {
        native.saved = "z".repeat(32)
        val error = assertFailsWith<MoodleException.Credentials> { store.load(site) }
        assertEquals("error.credentials.invalid", error.messageKey)
        assertTrue(native.freed)
        assertTrue(native.clearedBeforeFree)
    }

    @Test
    fun `native loading failures are sanitized`() {
        val store = WindowsCredentialTokenStore(nativeProvider = { throw UnsatisfiedLinkError("private diagnostic") })
        assertEquals(
            "error.credentials.unavailable",
            assertFailsWith<MoodleException.Credentials> { store.checkAvailable() }.messageKey
        )
    }

    @Test
    fun `platform selection is lazy and never loads Windows DLLs on macOS`() {
        assertIs<WindowsCredentialTokenStore>(platformTokenStore("Windows 11"))
        assertIs<MacKeychainTokenStore>(platformTokenStore("Mac OS X"))
    }
}

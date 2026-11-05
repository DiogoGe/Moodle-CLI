package moodle.infrastructure

import moodle.domain.AccessToken
import moodle.infrastructure.credentials.WindowsCredentialTokenStore
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@EnabledOnOs(OS.WINDOWS)
@EnabledIfEnvironmentVariable(named = "MOODLE_WINDOWS_CREDENTIAL_TESTS", matches = "true")
class WindowsCredentialNativeTest {
    @Test
    fun `Windows Credential Manager saves updates restores and removes isolated invented credentials`() {
        val store = WindowsCredentialTokenStore("moodle-cli-test-${UUID.randomUUID()}")
        val site = "https://fixture.invalid"
        try {
            store.checkAvailable()
            assertNull(store.load(site))
            store.save(site, AccessToken("a".repeat(32)))
            assertEquals("a".repeat(32), store.load(site)?.value)
            assertNull(store.load("https://different-fixture.invalid"))
            store.save(site, AccessToken("b".repeat(32)))
            assertEquals("b".repeat(32), store.load(site)?.value)
            store.delete(site)
            assertNull(store.load(site))
            store.delete(site)
        } finally {
            store.delete(site)
        }
    }
}

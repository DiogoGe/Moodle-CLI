package moodle.infrastructure

import moodle.domain.AccessToken
import moodle.infrastructure.credentials.MacKeychainTokenStore
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MacKeychainTokenStoreTest {
    @Test
    fun `native Keychain saves restores updates and deletes isolated test credentials`() {
        assumeTrue(System.getenv("MOODLE_KEYCHAIN_TESTS") == "true" && System.getProperty("os.name").startsWith("Mac"))
        val store = MacKeychainTokenStore("moodle-cli-test-${UUID.randomUUID()}")
        val site = "https://fixture.invalid"
        try {
            store.checkAvailable()
            assertNull(store.load(site))
            store.save(site, AccessToken("a".repeat(32)))
            assertEquals("a".repeat(32), store.load(site)?.value)
            store.save(site, AccessToken("b".repeat(32)))
            assertEquals("b".repeat(32), store.load(site)?.value)
            assertNull(store.load("https://another-fixture.invalid"))
            store.delete(site)
            assertNull(store.load(site))
            store.delete(site)
        } finally {
            store.delete(site)
        }
    }
}

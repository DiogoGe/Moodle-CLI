package moodle.application

import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.MoodleAccess
import moodle.fixtures.FakeTokenStore
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Timeout(15)
class ConcurrentAccessServiceTest {
    private val site = "https://fixture.invalid"
    private val token = AccessToken("a".repeat(32))

    @Test
    fun `parallel first calls restore once and execute outside the session lock`() {
        val validations = AtomicInteger()
        val store = FakeTokenStore().apply { values[site] = token }
        val access = AccessService(
            backend {
                validations.incrementAndGet()
                info()
            },
            site,
            store
        )
        val entered = CountDownLatch(4)
        val release = CountDownLatch(1)
        Executors.newFixedThreadPool(4).use { executor ->
            val calls = List(4) {
                executor.submit<Long> {
                    access.withSession { _, user ->
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        user
                    }
                }
            }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(1, validations.get())
            } finally {
                release.countDown()
            }
            calls.forEach { assertEquals(42L, it.get(5, TimeUnit.SECONDS)) }
        }
    }

    @Test
    fun `an old request cannot invalidate a newer login`() {
        val store = FakeTokenStore()
        val access = AccessService(backend { info() }, site, store)
        access.login(token)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newSingleThreadExecutor().use { executor ->
            val old = executor.submit {
                assertFailsWith<MoodleException.Authentication> {
                    access.withSession { _, _ ->
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        throw MoodleException.Authentication("error.session.expired")
                    }
                }
            }
            val replacement = AccessToken("b".repeat(32))
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                access.login(replacement)
            } finally {
                release.countDown()
            }
            old.get(5, TimeUnit.SECONDS)
            access.withSession { current, _ -> assertSame(replacement, current) }
            assertSame(replacement, store.values[site])
            assertEquals(0, store.deletes)
        }
    }

    @Test
    fun `a delayed health response cannot resurrect an ended session`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val validations = AtomicInteger()
        val access = AccessService(
            backend {
                if (validations.incrementAndGet() > 1) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                info()
            },
            site
        )
        access.login(token)
        Executors.newSingleThreadExecutor().use { executor ->
            val health = executor.submit<SiteInfo?> { access.getAuthenticatedStatus() }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                access.endSession()
            } finally {
                release.countDown()
            }
            health.get(5, TimeUnit.SECONDS)
            assertNull(access.getAuthenticatedStatus())
            assertTrue(access.getAvailableFunctions().isEmpty())
        }
    }

    private fun info(): SiteInfo = SiteInfo(site, "Fixture", 42, "Fixture user", setOf("core_enrol_get_users_courses"))

    private fun backend(validate: () -> SiteInfo): MoodleAccess = object : MoodleAccess {
        override fun getPublicStatus(): PublicStatus = error("Unused")
        override fun getSiteInfo(token: AccessToken): SiteInfo = validate()
    }
}

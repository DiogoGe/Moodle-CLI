package moodle.infrastructure.browser

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import moodle.domain.exceptions.MoodleException
import java.net.URI

/** Restricts browser interaction to one login tab and its expected Moodle callback page. */
internal class ChromeLoginPage private constructor(
    private val connection: ChromePipe,
    private val targetId: String,
    private val sessionId: String,
    private val callbackPage: MoodleCallbackPage
) {
    fun readCallback(): String? {
        // A closed tab must cancel login rather than leave an invisible browser waiting.
        connection.call("Target.getTargetInfo", buildJsonObject { put("targetId", targetId) })
        val response = connection.call(
            "Runtime.evaluate",
            buildJsonObject {
                put("expression", callbackPage.readCallback)
                put("returnByValue", true)
            },
            sessionId,
            allowCommandError = true
        )
        val evaluation = response?.get("result")?.jsonObject ?: return null
        return evaluation["value"]?.jsonPrimitive?.contentOrNull
    }

    companion object {
        fun open(connection: ChromePipe, launchUrl: URI): ChromeLoginPage {
            val contextId = createContext(connection)
            denyDownloads(connection, contextId)
            val targetId = createTarget(connection, contextId)
            val sessionId = attachToTarget(connection, targetId)
            val callbackPage = MoodleCallbackPage(launchUrl)
            configurePage(connection, sessionId, callbackPage, launchUrl)
            return ChromeLoginPage(connection, targetId, sessionId, callbackPage)
        }

        private fun createContext(connection: ChromePipe): String {
            val response = connection.call(
                "Target.createBrowserContext",
                buildJsonObject {
                    put("disposeOnDetach", true)
                }
            )
            return response.requiredString("browserContextId")
        }

        private fun denyDownloads(connection: ChromePipe, contextId: String) {
            connection.call(
                "Browser.setDownloadBehavior",
                buildJsonObject {
                    put("behavior", "deny")
                    put("browserContextId", contextId)
                }
            )
        }

        private fun createTarget(connection: ChromePipe, contextId: String): String {
            val response = connection.call(
                "Target.createTarget",
                buildJsonObject {
                    put("url", "about:blank")
                    put("browserContextId", contextId)
                }
            )
            return response.requiredString("targetId")
        }

        private fun attachToTarget(connection: ChromePipe, targetId: String): String {
            val response = connection.call(
                "Target.attachToTarget",
                buildJsonObject {
                    put("targetId", targetId)
                    put("flatten", true)
                }
            )
            return response.requiredString("sessionId")
        }

        private fun configurePage(
            connection: ChromePipe,
            sessionId: String,
            page: MoodleCallbackPage,
            launchUrl: URI
        ) {
            connection.call(
                "Page.addScriptToEvaluateOnNewDocument",
                buildJsonObject {
                    put("source", page.preventAppLaunch)
                },
                sessionId
            )
            connection.call("Page.navigate", buildJsonObject { put("url", launchUrl.toString()) }, sessionId)
        }

        private fun JsonObject?.requiredString(key: String): String = this?.get(key)?.jsonPrimitive?.contentOrNull
            ?: throw MoodleException.Authentication("error.browser.response")
    }
}

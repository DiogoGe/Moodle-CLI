package moodle.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import moodle.MoodleApplication
import moodle.cli.i18n.Messages
import moodle.domain.exceptions.MoodleException

internal class MoodleMcpTools(application: MoodleApplication, private val messages: Messages) {
    val tools: List<Tool> = McpSchemas.tools()
    private val queries = MoodleQueries(application)
    private val sessionLock = Mutex()

    suspend fun call(name: String, arguments: JsonObject): CallToolResult = sessionLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                val input = validatedArguments(name, arguments)
                val data = queries.execute(name, input)
                if (data.toString().toByteArray(Charsets.UTF_8).size > MAX_RESULT_BYTES) {
                    failure("error.mcp.resultTooLarge", messages.text("error.mcp.resultTooLarge"))
                } else {
                    result(data)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: MoodleException) {
                failure(error.messageKey, messages.error(error))
            } catch (_: Exception) {
                failure("error.mcp.failed", messages.text("error.mcp.failed"))
            } catch (_: LinkageError) {
                failure("error.credentials.unavailable", messages.text("error.credentials.unavailable"))
            }
        }
    }

    private fun validatedArguments(name: String, arguments: JsonObject): McpArguments {
        val definition = tools.singleOrNull { it.name == name } ?: throw MoodleException.Input("error.mcp.arguments")
        val properties = definition.inputSchema.properties ?: buildJsonObject {}
        val required = definition.inputSchema.required.orEmpty()
        if (required.any { it !in arguments }) throw MoodleException.Input("error.mcp.arguments")
        return McpArguments(arguments, properties.keys)
    }

    private fun result(data: JsonObject, error: Boolean = false): CallToolResult =
        CallToolResult(content = listOf(TextContent(data.toString())), structuredContent = data, isError = error)

    private fun failure(code: String, message: String): CallToolResult = result(
        buildJsonObject {
            put(
                "error",
                buildJsonObject {
                    put("code", code)
                    put("message", message)
                }
            )
        },
        error = true
    )

    private companion object {
        const val MAX_RESULT_BYTES = 262144
    }
}

package moodle.fixtures

import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class FailingMoodleCall(private val failure: IOException, val calls: AtomicInteger = AtomicInteger()) : Call<JsonObject> {
    private var cancelled = false
    private var executed = false

    override fun execute(): Response<JsonObject> {
        executed = true
        calls.incrementAndGet()
        throw failure
    }

    override fun clone(): Call<JsonObject> = FailingMoodleCall(failure, calls)

    override fun request(): Request = Request
        .Builder()
        .url("https://fixture.invalid/webservice/rest/server.php")
        .post(FormBody.Builder().add("wsfunction", "core_webservice_get_site_info").build())
        .build()

    override fun enqueue(callback: Callback<JsonObject>) = error("Synchronous fixture only")

    override fun cancel() {
        cancelled = true
    }

    override fun isCanceled(): Boolean = cancelled

    override fun isExecuted(): Boolean = executed

    override fun timeout(): Timeout = Timeout()
}

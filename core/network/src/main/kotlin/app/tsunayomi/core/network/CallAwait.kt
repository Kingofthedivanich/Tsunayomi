package app.tsunayomi.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Executes the call without blocking a thread; cancelling the coroutine cancels the request. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, _, _ -> response.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        },
    )
    continuation.invokeOnCancellation { cancel() }
}

class HttpException(val code: Int, message: String) : IOException("HTTP $code: $message")

/** Returns the body text of a successful response, otherwise throws [HttpException]. */
fun Response.bodyOrThrow(): String = use {
    if (!isSuccessful) throw HttpException(code, request.url.encodedPath)
    body.string()
}

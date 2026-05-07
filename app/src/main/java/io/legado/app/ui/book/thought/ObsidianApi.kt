package io.legado.app.ui.book.thought

import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

object ObsidianApi {

    private val markdownType = "text/markdown; charset=utf-8".toMediaType()

    suspend fun putFile(
        apiUrl: String,
        apiKey: String,
        filePath: String,
        content: String
    ): Result<Unit> = kotlin.runCatching {
        val encodedPath = filePath.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val url = "${apiUrl.trimEnd('/')}/vault/${encodedPath.trimStart('/')}"
        val body = content.toRequestBody(markdownType)
        okHttpClient.newCallResponse {
            url(url)
            put(body)
            addHeader("Authorization", "Bearer $apiKey")
            addHeader("Content-Type", "text/markdown")
        }
    }

    suspend fun checkConnection(
        apiUrl: String,
        apiKey: String
    ): Result<Boolean> = kotlin.runCatching {
        val url = "${apiUrl.trimEnd('/')}/"
        val response = okHttpClient.newCallResponse {
            url(url)
            get()
            addHeader("Authorization", "Bearer $apiKey")
        }
        response.isSuccessful
    }
}

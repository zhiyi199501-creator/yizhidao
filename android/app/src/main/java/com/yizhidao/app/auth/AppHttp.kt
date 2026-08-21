package com.yizhidao.app.auth

import android.content.Context
import com.yizhidao.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 用 Chromium 网络栈发 HTTPS。小米等机上 Java [java.net.HttpURLConnection]
 * 访问部分主机名（如已废的 yzh.codedance.work）会被 RST，同一台机器的浏览器（Cronet/QUIC）却能通。
 */
object AppHttp {
    private val executor: Executor = Executors.newCachedThreadPool()
    @Volatile
    private var engine: CronetEngine? = null

    fun init(context: Context) {
        if (engine != null) return
        engine = CronetEngine.Builder(context.applicationContext)
            .enableHttp2(true)
            .enableQuic(true)
            .addQuicHint("yd.codedance.work", 443, 443)
            .enableBrotli(true)
            .setUserAgent("Yizhidao/${BuildConfig.VERSION_NAME} (Android)")
            .build()
    }

    suspend fun request(
        url: String,
        method: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 20_000,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs.toLong()) {
                await(url, method, body, headers)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw SocketTimeoutException("timeout ${timeoutMs}ms").initCause(e)
        }
    }

    private suspend fun await(
        url: String,
        method: String,
        body: String?,
        headers: Map<String, String>,
    ): Pair<Int, String> = suspendCancellableCoroutine { cont ->
        val cronet = engine ?: run {
            cont.resumeWithException(IllegalStateException("AppHttp.init() was not called"))
            return@suspendCancellableCoroutine
        }
        val callback = object : UrlRequest.Callback() {
            private val bytes = ByteArrayOutputStream()

            override fun onRedirectReceived(
                request: UrlRequest,
                info: UrlResponseInfo,
                newLocationUrl: String,
            ) {
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                request.read(ByteBuffer.allocateDirect(32 * 1024))
            }

            override fun onReadCompleted(
                request: UrlRequest,
                info: UrlResponseInfo,
                byteBuffer: ByteBuffer,
            ) {
                byteBuffer.flip()
                val chunk = ByteArray(byteBuffer.remaining())
                byteBuffer.get(chunk)
                bytes.write(chunk)
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                if (cont.isActive) {
                    cont.resume(info.httpStatusCode to bytes.toString(Charsets.UTF_8.name()))
                }
            }

            override fun onFailed(
                request: UrlRequest,
                info: UrlResponseInfo?,
                error: CronetException,
            ) {
                if (cont.isActive) {
                    cont.resumeWithException(IOException(error.message ?: "Cronet failed", error))
                }
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                if (cont.isActive) {
                    cont.resumeWithException(CancellationException("request canceled"))
                }
            }
        }
        val builder = cronet.newUrlRequestBuilder(url, callback, executor).setHttpMethod(method)
        headers.forEach { (key, value) -> builder.addHeader(key, value) }
        if (body != null) {
            if (!headers.keys.any { it.equals("Content-Type", ignoreCase = true) }) {
                builder.addHeader("Content-Type", "application/json; charset=utf-8")
            }
            builder.setUploadDataProvider(
                UploadDataProviders.create(body.toByteArray(Charsets.UTF_8)),
                executor,
            )
        }
        val request = builder.build()
        cont.invokeOnCancellation { request.cancel() }
        request.start()
    }
}

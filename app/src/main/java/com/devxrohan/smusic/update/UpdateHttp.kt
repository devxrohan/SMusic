package com.devxrohan.smusic.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cancellable update requests with staggered transport fallbacks and DNS recovery. */
internal object UpdateHttp {

    private const val USER_AGENT_PREFIX = "SMusic/"

    private val userAgent: String
        get() = USER_AGENT_PREFIX + com.devxrohan.smusic.BuildConfig.VERSION_NAME

    // ---- DNS -------------------------------------------------------------

    /** System DNS, but IPv4 first: a dead IPv6 route is the most common silent Wi-Fi failure. */
    private object SystemDnsV4First : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            Dns.SYSTEM.lookup(hostname).sortedBy { it is Inet6Address }
    }

    /** Tiny DNS-over-HTTPS resolver (JSON API). Reached via IP literals, so it needs no DNS itself. */
    private object DohDns : Dns {
        private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
        private val endpoints = listOf(
            "https://1.1.1.1/dns-query?name={h}&type=A",
            "https://8.8.8.8/resolve?name={h}&type=A",
            "https://9.9.9.9:5053/dns-query?name={h}&type=A",
        )
        private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

        private val bootstrap: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .callTimeout(4, TimeUnit.SECONDS)
                .build()
        }

        override fun lookup(hostname: String): List<InetAddress> {
            val now = System.currentTimeMillis()
            cache[hostname]?.let { (at, ips) -> if (now - at < 10 * 60_000L) return ips }
            for (template in endpoints) {
                val ips = runCatching { query(template.replace("{h}", hostname)) }.getOrNull().orEmpty()
                if (ips.isNotEmpty()) {
                    cache[hostname] = now to ips
                    return ips
                }
            }
            throw UnknownHostException("DNS-over-HTTPS could not resolve $hostname")
        }

        private fun query(url: String): List<InetAddress> {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .build()
            bootstrap.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                val answers = JSONObject(body).optJSONArray("Answer") ?: return emptyList()
                val out = ArrayList<InetAddress>()
                for (i in 0 until answers.length()) {
                    val answer = answers.getJSONObject(i)
                    if (answer.optInt("type") != 1) continue // A records only
                    val data = answer.optString("data")
                    if (ipv4.matches(data)) out += InetAddress.getByName(data)
                }
                return out
            }
        }
    }

    // ---- Clients ---------------------------------------------------------

    private fun baseBuilder(callTimeoutSeconds: Long) = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)

    private val systemClient: OkHttpClient by lazy { baseBuilder(8).dns(SystemDnsV4First).build() }
    private val dohClient: OkHttpClient by lazy { baseBuilder(8).dns(DohDns).build() }

    /** Use read-stall limits for APK downloads; a total call timeout would abort slow healthy transfers. */
    private fun downloadBuilder() = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)

    val systemDownloadClient: OkHttpClient by lazy { downloadBuilder().dns(SystemDnsV4First).build() }
    val dohDownloadClient: OkHttpClient by lazy { downloadBuilder().dns(DohDns).build() }

    // ---- Requests --------------------------------------------------------

    fun request(url: String, accept: String? = null): Request = Request.Builder()
        .url(url)
        .header("User-Agent", userAgent)
        .header("Cache-Control", "no-cache")
        .apply { if (accept != null) header("Accept", accept) }
        .build()

    /** Cancellable blocking call: cancelling the coroutine cancels the socket. */
    suspend fun Call.executeCancellable(): Response = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { runCatching { cancel() } }
            try {
                cont.resume(execute())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }
    }

    /** Parse a GET response using system DNS, then DoH; stop on HTTP errors or exhausted transports. */
    suspend fun <T : Any> fetch(
        url: String,
        accept: String? = null,
        parse: (body: String, finalUrl: String) -> T?,
    ): T? = withContext(Dispatchers.IO) {
        for (client in listOf(systemClient, dohClient)) {
            val result: T? = try {
                client.newCall(request(url, accept)).executeCancellable().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    parse(response.body?.string().orEmpty(), response.request.url.toString())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (result != null) return@withContext result
        }
        null
    }

    // ---- Racing ----------------------------------------------------------

    /** Race transports in priority order, starting a fallback after its delay or predecessor failure. */
    class Step<T : Any>(val delayMs: Long, val block: suspend () -> T?)

    fun <T : Any> step(delayMs: Long, block: suspend () -> T?): Step<T> = Step(delayMs, block)

    suspend fun <T : Any> race(vararg steps: Step<T>): T? = coroutineScope {
        if (steps.isEmpty()) return@coroutineScope null
        val winner = CompletableDeferred<T?>()
        val kicks = List(steps.size) { CompletableDeferred<Unit>() }
        kicks[0].complete(Unit)
        val jobs = steps.mapIndexed { index, step ->
            launch {
                if (index > 0) withTimeoutOrNull(step.delayMs) { kicks[index].await() }
                val result = try {
                    step.block()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null
                }
                if (result != null) {
                    winner.complete(result)
                } else if (index + 1 < kicks.size) {
                    kicks[index + 1].complete(Unit)
                }
            }
        }
        launch {
            jobs.joinAll()
            winner.complete(null)
        }
        val result = winner.await()
        coroutineContext.cancelChildren()
        result
    }
}

package com.antnexo.core

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant

class WhatsNewTest {
    private val now = Instant.parse("2026-11-22T12:00:00Z")
    private fun daysAgo(d: Long) = now.minus(Duration.ofDays(d)).toEpochMilli()

    @Test
    fun firstRunCountsTheLast30Days() =
        assertEquals(2, unreadCount(listOf(daysAgo(1), daysAgo(29), daysAgo(31)), seen = null, now = now.toEpochMilli()))

    @Test
    fun countsOnlyNewerThanSeen() {
        val dates = listOf(daysAgo(1), daysAgo(5), daysAgo(40))
        assertEquals(1, unreadCount(dates, seen = daysAgo(5), now = now.toEpochMilli()))
        assertEquals(0, unreadCount(dates, seen = now.toEpochMilli(), now = now.toEpochMilli()))
        assertEquals(0, unreadCount(emptyList(), seen = null, now = now.toEpochMilli()))
    }

    private var server: HttpServer? = null
    private var request: Map<String, String?> = emptyMap()

    private fun serve(status: Int, body: String): BoardHost {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            request = mapOf(
                "method" to ex.requestMethod,
                "uri" to ex.requestURI.toString(),
                "accept" to ex.requestHeaders.getFirst("Accept"),
                "authorization" to ex.requestHeaders.getFirst("Authorization"),
                "cookie" to ex.requestHeaders.getFirst("Cookie"),
            )
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        s.start()
        server = s
        return BoardHost.parse("http://127.0.0.1:${s.address.port}")
    }

    @After
    fun stop() {
        server?.stop(0)
    }

    @Test
    fun readsThePublicChangelog() = runBlocking {
        val host = serve(200, """{"has_more":false,"items":[{"published_at":"2026-10-13T14:47:34.251324Z","title":"x"},
            {"published_at":"nope"},{"published_at":"2026-10-01T00:00:00Z"}]}""")
        val dates = ChangelogClient(host).publishedDates()
        assertEquals(listOf(Instant.parse("2026-10-13T14:47:34.251Z").toEpochMilli(), Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()), dates)
        assertEquals("GET", request["method"])
        assertEquals("/api/v1/public/changelog?limit=20", request["uri"])
        assertEquals("application/json", request["accept"])
        assertNull(request["authorization"])
        assertNull(request["cookie"])
    }

    @Test
    fun anErrorStatusThrows() {
        val host = serve(500, "{}")
        val e = assertThrows(AntNexoException.Http::class.java) { runBlocking { ChangelogClient(host).publishedDates() } }
        assertEquals(500, e.status)
    }

    @Test
    fun aBodyThatIsntTheChangelogThrows() {
        for (body in listOf("<html>", """{"items":3}""", "[]")) {
            val host = serve(200, body)
            assertThrows(body, AntNexoException.BadResponse::class.java) {
                runBlocking { ChangelogClient(host).publishedDates() }
            }
            stop()
        }
    }

    @Test
    fun aServerThatNeverAnswersTimesOut() {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { Thread.sleep(5_000) }
        s.start()
        server = s
        val started = System.nanoTime()
        assertThrows(IOException::class.java) {
            runBlocking { ChangelogClient(BoardHost.parse("http://127.0.0.1:${s.address.port}"), timeoutMs = 200).publishedDates() }
        }
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(3))
    }

    @Test
    fun fittedSizes() {
        assertEquals(Size(738, 1600), fittedSize(1179, 2556))
        assertEquals(Size(1600, 533), fittedSize(3000, 1000))
        assertEquals(Size(800, 600), fittedSize(800, 600))
        assertEquals(Size(0, 0), fittedSize(0, 0))
    }
}

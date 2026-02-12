package org.organicprogramming.holons

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HolonsTest {
    @Test fun schemeExtraction() {
        assertEquals("tcp", Transport.scheme("tcp://:9090"))
        assertEquals("unix", Transport.scheme("unix:///tmp/x.sock"))
        assertEquals("stdio", Transport.scheme("stdio://"))
        assertEquals("mem", Transport.scheme("mem://"))
        assertEquals("ws", Transport.scheme("ws://127.0.0.1:8080/grpc"))
        assertEquals("wss", Transport.scheme("wss://example.com:443/grpc"))
    }

    @Test fun defaultUri() {
        assertEquals("tcp://:9090", Transport.DEFAULT_URI)
    }

    @Test fun tcpListen() {
        val lis = Transport.listen("tcp://127.0.0.1:0")
        val tcp = lis as Transport.Listener.Tcp
        assertTrue(tcp.socket.localPort > 0)
        tcp.socket.close()
    }

    @Test fun parseUriWss() {
        val parsed = Transport.parseURI("wss://example.com:8443")
        assertEquals("wss", parsed.scheme)
        assertEquals("example.com", parsed.host)
        assertEquals(8443, parsed.port)
        assertEquals("/grpc", parsed.path)
        assertTrue(parsed.secure)
    }

    @Test fun stdioAndMemListenVariants() {
        assertEquals(Transport.Listener.Stdio, Transport.listen("stdio://"))
        assertEquals(Transport.Listener.Mem, Transport.listen("mem://"))
    }

    @Test fun wsListenVariant() {
        val ws = Transport.listen("ws://127.0.0.1:8080/holon") as Transport.Listener.WS
        assertEquals("127.0.0.1", ws.host)
        assertEquals(8080, ws.port)
        assertEquals("/holon", ws.path)
        assertTrue(!ws.secure)
    }

    @Test fun unsupportedUri() {
        assertFailsWith<IllegalArgumentException> { Transport.listen("ftp://host") }
    }

    @Test fun parseFlagsListen() {
        assertEquals("tcp://:8080", Serve.parseFlags(arrayOf("--listen", "tcp://:8080")))
    }

    @Test fun parseFlagsPort() {
        assertEquals("tcp://:3000", Serve.parseFlags(arrayOf("--port", "3000")))
    }

    @Test fun parseFlagsDefault() {
        assertEquals(Transport.DEFAULT_URI, Serve.parseFlags(emptyArray()))
    }

    @Test fun parseHolon() {
        val tmp = File.createTempFile("holon", ".md")
        tmp.writeText(
            "---\nuuid: \"abc-123\"\ngiven_name: \"test\"\n" +
            "family_name: \"Test\"\nlang: \"kotlin\"\n---\n# test\n"
        )
        val id = Identity.parseHolon(tmp.absolutePath)
        assertEquals("abc-123", id.uuid)
        assertEquals("test", id.givenName)
        assertEquals("kotlin", id.lang)
        tmp.delete()
    }

    @Test fun parseMissingFrontmatter() {
        val tmp = File.createTempFile("nofm", ".md")
        tmp.writeText("# No frontmatter\n")
        assertFailsWith<IllegalArgumentException> { Identity.parseHolon(tmp.absolutePath) }
        tmp.delete()
    }
}

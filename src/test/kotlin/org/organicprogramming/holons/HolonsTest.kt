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
    }

    @Test fun defaultUri() {
        assertEquals("tcp://:9090", Transport.DEFAULT_URI)
    }

    @Test fun tcpListen() {
        val ss = Transport.listen("tcp://127.0.0.1:0")
        assertTrue(ss.localPort > 0)
        ss.close()
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

package org.organicprogramming.holons

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
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
        assertTrue(Transport.listen("mem://") is Transport.Listener.Mem)
    }

    @Test fun unixListenAndDialRoundTrip() {
        val path = File.createTempFile("holons-kotlin", ".sock").absolutePath
        File(path).delete()
        val uri = "unix://$path"

        val lis = Transport.listen(uri) as Transport.Listener.Unix
        val serverError = arrayOfNulls<Throwable>(1)

        val server = Thread {
            try {
                lis.channel.accept().use { accepted ->
                    val input = ByteBuffer.allocate(4)
                    while (input.hasRemaining()) {
                        accepted.read(input)
                    }
                    input.flip()
                    while (input.hasRemaining()) {
                        accepted.write(input)
                    }
                }
            } catch (t: Throwable) {
                serverError[0] = t
            }
        }
        server.start()

        Transport.dialUnix(uri).use { client ->
            client.write(ByteBuffer.wrap("ping".toByteArray(StandardCharsets.UTF_8)))
            val out = ByteBuffer.allocate(4)
            while (out.hasRemaining()) {
                client.read(out)
            }
            assertEquals("ping", String(out.array(), StandardCharsets.UTF_8))
        }

        server.join(3000)
        lis.channel.close()
        serverError[0]?.let { throw AssertionError("unix server failed", it) }
    }

    @Test fun memListenAndDialRoundTrip() {
        val lis = Transport.listen("mem://") as Transport.Listener.Mem
        Transport.memDial(lis).use { client ->
            lis.runtime.accept(1000).use { server ->
                client.output.write("hola".toByteArray(StandardCharsets.UTF_8))
                client.output.flush()

                val inbound = server.input.readNBytes(4)
                assertEquals("hola", String(inbound, StandardCharsets.UTF_8))

                server.output.write(inbound)
                server.output.flush()

                val out = client.input.readNBytes(4)
                assertEquals("hola", String(out, StandardCharsets.UTF_8))
            }
        }
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

    @Test fun certDeclaresEchoClientAndDialCapabilities() {
        val cert = File("cert.json").readText()
        assertTrue(cert.contains("\"echo_client\": \"./bin/echo-client\""))
        assertTrue(cert.contains("\"grpc_dial_tcp\": true"))
        assertTrue(cert.contains("\"grpc_dial_stdio\": true"))
        assertTrue(cert.contains("\"grpc_dial_ws\": true"))
    }

    @Test fun certDeclaresEchoServerAndListenCapabilities() {
        val cert = File("cert.json").readText()
        assertTrue(cert.contains("\"echo_server\": \"./bin/echo-server\""))
        assertTrue(cert.contains("\"grpc_listen_tcp\": true"))
        assertTrue(cert.contains("\"grpc_listen_stdio\": true"))
        assertTrue(cert.contains("\"grpc_reject_oversize\": true"))
    }

    @Test fun certDeclaresHolonRpcServerExecutableAndCapability() {
        val cert = File("cert.json").readText()
        assertTrue(cert.contains("\"holon_rpc_server\": \"./bin/holon-rpc-server\""))
        assertTrue(cert.contains("\"holon_rpc_server\": true"))
    }

    @Test fun echoClientScriptUsesGoHelperAndDefaultGocache() {
        val run = runEchoClientScript(
            args = listOf("--message", "cert", "tcp://127.0.0.1:19090"),
            gocache = null,
        )

        assertEquals(0, run.exitCode)
        assertTrue(run.stdout.contains("\"status\":\"pass\""))
        assertEquals(
            File("..", "go-holons").canonicalPath,
            File(run.workingDirectory).canonicalPath,
        )
        assertEquals("/tmp/go-cache", run.gocache)

        assertEquals("run", run.arguments[0])
        assertTrue(run.arguments[1].endsWith("/kotlin-holons/cmd/echo-client-go/main.go"))
        assertEquals("--sdk", run.arguments[2])
        assertEquals("kotlin-holons", run.arguments[3])
        assertEquals("--server-sdk", run.arguments[4])
        assertEquals("go-holons", run.arguments[5])
        assertEquals("--message", run.arguments[6])
        assertEquals("cert", run.arguments[7])
        assertEquals("tcp://127.0.0.1:19090", run.arguments[8])
    }

    @Test fun echoClientScriptPreservesProvidedGocache() {
        val run = runEchoClientScript(
            args = listOf("stdio://"),
            gocache = "/tmp/kotlin-holons-custom-cache",
        )

        assertEquals(0, run.exitCode)
        assertEquals("/tmp/kotlin-holons-custom-cache", run.gocache)
    }

    @Test fun echoClientScriptForwardsWebSocketTarget() {
        val run = runEchoClientScript(
            args = listOf("--message", "cert", "ws://127.0.0.1:28080/grpc"),
            gocache = null,
        )

        assertEquals(0, run.exitCode)
        assertTrue(run.arguments.contains("ws://127.0.0.1:28080/grpc"))
    }

    @Test fun echoServerScriptUsesGoHelperAndDefaultGocache() {
        val run = runEchoServerScript(
            args = listOf("--listen", "tcp://127.0.0.1:0"),
            gocache = null,
        )

        assertEquals(0, run.exitCode)
        assertEquals(
            File("..", "go-holons").canonicalPath,
            File(run.workingDirectory).canonicalPath,
        )
        assertEquals("/tmp/go-cache", run.gocache)

        assertEquals("run", run.arguments[0])
        assertTrue(run.arguments[1].endsWith("/kotlin-holons/cmd/echo-server-go/main.go"))
        assertEquals("--sdk", run.arguments[2])
        assertEquals("kotlin-holons", run.arguments[3])
        assertEquals("--listen", run.arguments[4])
        assertEquals("tcp://127.0.0.1:0", run.arguments[5])
    }

    @Test fun echoServerScriptPreservesServeModeArguments() {
        val run = runEchoServerScript(
            args = listOf("serve", "--listen", "stdio://", "--version", "0.2.0"),
            gocache = "/tmp/kotlin-holons-custom-cache",
        )

        assertEquals(0, run.exitCode)
        assertEquals("/tmp/kotlin-holons-custom-cache", run.gocache)
        assertEquals("run", run.arguments[0])
        assertTrue(run.arguments[1].endsWith("/kotlin-holons/cmd/echo-server-go/main.go"))
        assertEquals("serve", run.arguments[2])
        assertEquals("--sdk", run.arguments[3])
        assertEquals("kotlin-holons", run.arguments[4])
        assertEquals("--listen", run.arguments[5])
        assertEquals("stdio://", run.arguments[6])
        assertEquals("--version", run.arguments[7])
        assertEquals("0.2.0", run.arguments[8])
    }

    @Test fun holonRpcServerScriptInjectsDefaultSdkAndDefaultGocache() {
        val run = runHolonRpcServerScript(
            args = listOf("ws://127.0.0.1:8080/rpc", "--once"),
            gocache = null,
        )

        assertEquals(0, run.exitCode)
        assertEquals(
            File("..", "go-holons").canonicalPath,
            File(run.workingDirectory).canonicalPath,
        )
        assertEquals("/tmp/go-cache", run.gocache)

        assertEquals("run", run.arguments[0])
        assertTrue(run.arguments[1].endsWith("/kotlin-holons/cmd/holon-rpc-server-go/main.go"))
        assertEquals("--sdk", run.arguments[2])
        assertEquals("kotlin-holons", run.arguments[3])
        assertEquals("ws://127.0.0.1:8080/rpc", run.arguments[4])
        assertEquals("--once", run.arguments[5])
    }

    @Test fun holonRpcServerScriptPreservesExplicitSdk() {
        val run = runHolonRpcServerScript(
            args = listOf("--sdk", "override-sdk", "ws://127.0.0.1:9090/rpc"),
            gocache = "/tmp/kotlin-holons-custom-cache",
        )

        assertEquals(0, run.exitCode)
        assertEquals("/tmp/kotlin-holons-custom-cache", run.gocache)
        assertEquals("run", run.arguments[0])
        assertTrue(run.arguments[1].endsWith("/kotlin-holons/cmd/holon-rpc-server-go/main.go"))
        assertEquals("--sdk", run.arguments[2])
        assertEquals("override-sdk", run.arguments[3])
    }

    private fun runEchoClientScript(
        args: List<String>,
        gocache: String?,
    ): ScriptRun {
        return runScript("bin/echo-client", args, gocache)
    }

    private fun runEchoServerScript(
        args: List<String>,
        gocache: String?,
    ): ScriptRun {
        return runScript("bin/echo-server", args, gocache)
    }

    private fun runHolonRpcServerScript(
        args: List<String>,
        gocache: String?,
    ): ScriptRun {
        return runScript("bin/holon-rpc-server", args, gocache)
    }

    private fun runScript(
        scriptPath: String,
        args: List<String>,
        gocache: String?,
    ): ScriptRun {
        val script = File(scriptPath)
        assertTrue(script.exists(), "missing ${script.path}")
        assertTrue(script.canExecute(), "not executable: ${script.path}")

        val tmpDir = Files.createTempDirectory("kotlin-echo-client-test").toFile()
        val argsFile = File(tmpDir, "args.txt")
        val pwdFile = File(tmpDir, "pwd.txt")
        val gocacheFile = File(tmpDir, "gocache.txt")
        val fakeGo = File(tmpDir, "go")

        fakeGo.writeText(
            """
            #!/usr/bin/env bash
            set -euo pipefail
            printf '%s\n' "${'$'}PWD" > "${pwdFile.absolutePath}"
            printf '%s\n' "${'$'}{GOCACHE:-}" > "${gocacheFile.absolutePath}"
            : > "${argsFile.absolutePath}"
            for arg in "${'$'}@"; do
              printf '%s\n' "${'$'}arg" >> "${argsFile.absolutePath}"
            done
            printf '%s\n' '{"status":"pass","sdk":"kotlin-holons","server_sdk":"go-holons"}'
            """.trimIndent(),
        )
        fakeGo.setExecutable(true)

        try {
            val process = ProcessBuilder(listOf(script.absolutePath) + args)
                .redirectErrorStream(true)
                .directory(File(System.getProperty("user.dir")))
                .apply {
                    environment()["GO_BIN"] = fakeGo.absolutePath
                    if (gocache == null) {
                        environment().remove("GOCACHE")
                    } else {
                        environment()["GOCACHE"] = gocache
                    }
                }
                .start()

            val stdout = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
            val exitCode = process.waitFor()

            return ScriptRun(
                exitCode = exitCode,
                stdout = stdout,
                arguments = if (argsFile.exists()) argsFile.readLines() else emptyList(),
                workingDirectory = if (pwdFile.exists()) pwdFile.readText().trim() else "",
                gocache = if (gocacheFile.exists()) gocacheFile.readText().trim() else "",
            )
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private data class ScriptRun(
        val exitCode: Int,
        val stdout: String,
        val arguments: List<String>,
        val workingDirectory: String,
        val gocache: String,
    )
}

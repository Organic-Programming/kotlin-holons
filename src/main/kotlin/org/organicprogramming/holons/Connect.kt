package org.organicprogramming.holons

import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Optional
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

data class ConnectOptions(
    val timeout: Duration = Duration.ofSeconds(5),
    val transport: String = "tcp",
    val start: Boolean = true,
    val portFile: Path? = null,
)

object Connect {
    private data class StartedHandle(
        val process: Process,
        val ephemeral: Boolean,
    )

    private data class StartedProcess(
        val uri: String,
        val process: Process,
    )

    private data class HostPort(
        val host: String,
        val port: Int,
    )

    private val started = Collections.synchronizedMap(IdentityHashMap<ManagedChannel, StartedHandle>())

    suspend fun connect(target: String): ManagedChannel = withContext(Dispatchers.IO) {
        connectInternal(target, ConnectOptions(), ephemeral = true)
    }

    suspend fun connect(target: String, options: ConnectOptions): ManagedChannel = withContext(Dispatchers.IO) {
        connectInternal(target, options, ephemeral = false)
    }

    suspend fun disconnect(channel: ManagedChannel?) = withContext(Dispatchers.IO) {
        if (channel == null) return@withContext

        val handle = synchronized(started) { started.remove(channel) }
        channel.shutdownNow()
        runCatching { channel.awaitTermination(2, TimeUnit.SECONDS) }

        if (handle?.ephemeral == true) {
            stopProcess(handle.process)
        }
    }

    private fun connectInternal(target: String, options: ConnectOptions, ephemeral: Boolean): ManagedChannel {
        val trimmed = target.trim()
        require(trimmed.isNotEmpty()) { "target is required" }

        val transport = options.transport.trim().ifEmpty { "tcp" }.lowercase()
        require(transport == "tcp") { "unsupported transport \"$transport\"" }

        val timeout = if (options.timeout.isZero || options.timeout.isNegative) Duration.ofSeconds(5) else options.timeout

        if (isDirectTarget(trimmed)) {
            return dialReady(normalizeDialTarget(trimmed), timeout)
        }

        val entry = Discover.findBySlug(trimmed)
            ?: throw IllegalStateException("holon \"$trimmed\" not found")

        val portFile = options.portFile ?: defaultPortFilePath(entry.slug)
        val reusable = usablePortFile(portFile, timeout)
        if (reusable != null) {
            return dialReady(normalizeDialTarget(reusable), timeout)
        }
        check(options.start) { "holon \"$trimmed\" is not running" }

        val binaryPath = resolveBinaryPath(entry)
        val startedProcess = startTcpHolon(binaryPath, timeout)

        val channel = try {
            dialReady(normalizeDialTarget(startedProcess.uri), timeout)
        } catch (t: Throwable) {
            stopProcess(startedProcess.process)
            throw t
        }

        if (!ephemeral) {
            try {
                writePortFile(portFile, startedProcess.uri)
            } catch (t: Throwable) {
                channel.shutdownNow()
                stopProcess(startedProcess.process)
                throw t
            }
        }

        synchronized(started) {
            started[channel] = StartedHandle(startedProcess.process, ephemeral)
        }
        return channel
    }

    private fun dialReady(target: String, timeout: Duration): ManagedChannel {
        val channel = if (target.startsWith("unix://")) {
            ManagedChannelBuilder.forTarget(target).usePlaintext().build()
        } else {
            val hostPort = parseHostPort(target)
            ManagedChannelBuilder.forAddress(hostPort.host, hostPort.port).usePlaintext().build()
        }

        return try {
            waitForReady(channel, timeout)
            channel
        } catch (t: Throwable) {
            channel.shutdownNow()
            throw t
        }
    }

    private fun waitForReady(channel: ManagedChannel, timeout: Duration) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var state = channel.getState(true)
        while (state != ConnectivityState.READY) {
            check(state != ConnectivityState.SHUTDOWN) { "gRPC channel shut down before becoming ready" }

            val remaining = deadline - System.nanoTime()
            check(remaining > 0) { "timed out waiting for gRPC readiness" }

            val latch = CountDownLatch(1)
            channel.notifyWhenStateChanged(state) { latch.countDown() }
            if (!latch.await(remaining, TimeUnit.NANOSECONDS)) {
                error("timed out waiting for gRPC readiness")
            }
            state = channel.getState(false)
        }
    }

    private fun usablePortFile(portFile: Path, timeout: Duration): String? {
        return try {
            val raw = Files.readString(portFile).trim()
            if (raw.isEmpty()) {
                Files.deleteIfExists(portFile)
                return null
            }

            val probe = dialReady(normalizeDialTarget(raw), minOf(timeout, Duration.ofSeconds(1)))
            probe.shutdownNow()
            raw
        } catch (_: Throwable) {
            runCatching { Files.deleteIfExists(portFile) }
            null
        }
    }

    private fun startTcpHolon(binaryPath: String, timeout: Duration): StartedProcess {
        val process = ProcessBuilder(binaryPath, "serve", "--listen", "tcp://127.0.0.1:0").start()
        val lines: BlockingQueue<String> = LinkedBlockingQueue()
        val stderr = StringBuilder()

        startReader(process.inputStream, lines, null)
        startReader(process.errorStream, lines, stderr)

        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) {
                throw IOException("holon exited before advertising an address: ${stderr.toString().trim()}")
            }

            val line = lines.poll(50, TimeUnit.MILLISECONDS) ?: continue
            val uri = firstUri(line)
            if (uri.isNotBlank()) {
                return StartedProcess(uri, process)
            }
        }

        stopProcess(process)
        throw IOException("timed out waiting for holon startup")
    }

    private fun startReader(stream: InputStream, lines: BlockingQueue<String>, capture: StringBuilder?) {
        Thread {
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    capture?.append(line)?.append('\n')
                    lines.offer(line)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun resolveBinaryPath(entry: HolonEntry): String {
        val manifest = entry.manifest ?: error("holon \"${entry.slug}\" has no manifest")
        val binary = manifest.artifacts.binary.trim()
        require(binary.isNotEmpty()) { "holon \"${entry.slug}\" has no artifacts.binary" }

        val configured = Path.of(binary)
        if (configured.isAbsolute && Files.isRegularFile(configured)) {
            return configured.toString()
        }

        val candidate = entry.dir.resolve(".op").resolve("build").resolve("bin").resolve(configured.fileName)
        if (Files.isRegularFile(candidate)) {
            return candidate.toString()
        }

        val pathEnv = System.getenv("PATH") ?: ""
        pathEnv.split(File.pathSeparator).forEach { dir ->
            val resolved = Path.of(dir).resolve(configured.fileName.toString())
            if (Files.isRegularFile(resolved) && Files.isExecutable(resolved)) {
                return resolved.toString()
            }
        }

        error("built binary not found for holon \"${entry.slug}\"")
    }

    private fun defaultPortFilePath(slug: String): Path =
        Path.of(System.getProperty("user.dir", ".")).resolve(".op").resolve("run").resolve("$slug.port")

    private fun writePortFile(portFile: Path, uri: String) {
        Files.createDirectories(portFile.parent)
        Files.writeString(portFile, uri.trim() + System.lineSeparator())
    }

    private fun stopProcess(process: Process?) {
        if (process == null || !process.isAlive) return

        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
        }
    }

    private fun isDirectTarget(target: String): Boolean =
        target.contains("://") || target.contains(':')

    private fun normalizeDialTarget(target: String): String {
        if (!target.contains("://")) return target

        val parsed = Transport.parseURI(target)
        return when (parsed.scheme) {
            "tcp" -> {
                val host = if (parsed.host.isNullOrBlank() || parsed.host == "0.0.0.0") "127.0.0.1" else parsed.host
                "$host:${parsed.port}"
            }
            "unix" -> "unix://${parsed.path}"
            else -> target
        }
    }

    private fun firstUri(line: String): String {
        for (field in line.split(Regex("\\s+"))) {
            val trimmed = field.trim().trim('"', '\'', '(', ')', '[', ']', '{', '}', '.', ',')
            if (trimmed.startsWith("tcp://") ||
                trimmed.startsWith("unix://") ||
                trimmed.startsWith("stdio://") ||
                trimmed.startsWith("ws://") ||
                trimmed.startsWith("wss://")
            ) {
                return trimmed
            }
        }
        return ""
    }

    private fun parseHostPort(target: String): HostPort {
        val index = target.lastIndexOf(':')
        require(index > 0 && index < target.length - 1) { "invalid host:port target \"$target\"" }
        return HostPort(target.substring(0, index), target.substring(index + 1).toInt())
    }
}

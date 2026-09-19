package maryk.datastore.remote

import maryk.datastore.shared.runCatchingNonFatal
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

actual fun defaultSshTunnelFactory(): SshTunnelFactory? = ProcessSshTunnelFactory

private object ProcessSshTunnelFactory : SshTunnelFactory {
    override fun open(config: RemoteSshConfig, target: SshTarget): SshTunnel {
        val localPort = config.localPort?.takeIf { it > 0 } ?: allocateLocalPort()
        if (config.localPort != null && !isLocalPortAvailable(localPort)) {
            throw IllegalStateException("SSH local port $localPort is already in use")
        }
        val command = buildCommand(config, target, localPort)
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val diagnostics = SshDiagnostics()
        drainOutput(process.inputStream, diagnostics)
        try {
            waitForLocalPort(process, localPort, diagnostics)
        } catch (error: Throwable) {
            destroyProcess(process)
            throw error
        }

        return ProcessSshTunnel(process, localPort)
    }

    private fun allocateLocalPort(): Int = ServerSocket(0).use { it.localPort }

    private fun isLocalPortAvailable(port: Int): Boolean = runCatchingNonFatal {
        ServerSocket().use { socket ->
            socket.bind(InetSocketAddress("127.0.0.1", port))
        }
    }.isSuccess

    private fun buildCommand(config: RemoteSshConfig, target: SshTarget, localPort: Int): List<String> {
        val command = mutableListOf(
            "ssh",
            "-N",
            "-T",
            "-o",
            "ExitOnForwardFailure=yes",
            "-L",
            "127.0.0.1:$localPort:${target.host.forSshForwarding()}:${target.port}",
        )

        if (config.port != 22) {
            command += listOf("-p", config.port.toString())
        }
        config.identityFile?.takeIf { it.isNotBlank() }?.let { file ->
            command += listOf("-i", file)
        }
        if (config.extraArgs.isNotEmpty()) {
            command += config.extraArgs
        }

        val hostTarget = config.user?.takeIf { it.isNotBlank() }?.let { user ->
            "$user@${config.host}"
        } ?: config.host

        command += hostTarget
        return command
    }

    private fun drainOutput(stream: InputStream, diagnostics: SshDiagnostics) {
        Thread {
            runCatchingNonFatal {
                val buffer = ByteArray(4096)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    diagnostics.append(buffer, count)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun waitForLocalPort(process: Process, localPort: Int, diagnostics: SshDiagnostics) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) {
                throw IllegalStateException("SSH tunnel process exited with code ${process.exitValue()}${diagnostics.suffix()}")
            }
            if (runCatchingNonFatal {
                Socket("127.0.0.1", localPort).use {}
            }.isSuccess) {
                waitForStableProcess(process, localPort, diagnostics)
                return
            }
            Thread.sleep(50)
        }
        throw IllegalStateException("SSH tunnel did not open local port $localPort within timeout")
    }

    private fun waitForStableProcess(process: Process, localPort: Int, diagnostics: SshDiagnostics) {
        repeat(5) {
            Thread.sleep(50)
            if (!process.isAlive) {
                throw IllegalStateException(
                    "SSH tunnel process exited after opening local port $localPort with code ${process.exitValue()}${diagnostics.suffix()}"
                )
            }
        }
    }
}

internal class SshDiagnostics {
    private val output = StringBuilder()

    fun append(bytes: ByteArray, count: Int) = synchronized(output) {
        val remaining = maxDiagnosticsBytes - output.length
        if (remaining > 0) output.append(bytes.decodeToString(0, minOf(count, remaining)))
    }

    fun suffix(): String = synchronized(output) {
        output.toString()
            .replace(sshDiagnosticSecret, "\$1\$2<redacted>")
            .replace(sshDiagnosticWhitespace, " ")
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { ": $it" }
            ?: ""
    }
}

private const val maxDiagnosticsBytes = 4_096
private val sshDiagnosticSecret = Regex("(?i)\\b(password|passphrase|token|authorization)\\s*([=:])\\s*\\S+")
private val sshDiagnosticWhitespace = Regex("[\\r\\n\\t]+")

private fun String.forSshForwarding(): String =
    if (':' in this && !startsWith("[")) "[$this]" else this

private class ProcessSshTunnel(
    private val process: Process,
    override val localPort: Int,
) : SshTunnel {
    override val isActive: Boolean
        get() = process.isAlive

    override fun close() {
        destroyProcess(process)
    }
}

private fun destroyProcess(process: Process) {
    process.destroy()
    try {
        process.waitFor(3, TimeUnit.SECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    }
    if (process.isAlive) {
        process.destroyForcibly()
        try {
            process.waitFor(3, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}

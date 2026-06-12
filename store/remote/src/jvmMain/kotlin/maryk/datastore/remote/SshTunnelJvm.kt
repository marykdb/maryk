package maryk.datastore.remote

import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import maryk.datastore.shared.rethrowIfFatal

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

        drainOutput(process.inputStream)
        try {
            waitForLocalPort(process, localPort)
        } catch (error: Throwable) {
            destroyProcess(process)
            throw error
        }

        return ProcessSshTunnel(process, localPort)
    }

    private fun allocateLocalPort(): Int = ServerSocket(0).use { it.localPort }

    private fun isLocalPortAvailable(port: Int): Boolean = runCatching {
        ServerSocket().use { socket ->
            socket.bind(InetSocketAddress("127.0.0.1", port))
        }
    }.onFailure {
        it.rethrowIfFatal()
    }.isSuccess

    private fun buildCommand(config: RemoteSshConfig, target: SshTarget, localPort: Int): List<String> {
        val command = mutableListOf(
            "ssh",
            "-N",
            "-T",
            "-o",
            "ExitOnForwardFailure=yes",
            "-L",
            "$localPort:${target.host}:${target.port}",
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

    private fun drainOutput(stream: InputStream) {
        Thread {
            runCatching {
                val buffer = ByteArray(4096)
                while (stream.read(buffer) >= 0) {
                    // Discard ssh output while keeping pipe drained.
                }
            }.onFailure { it.rethrowIfFatal() }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun waitForLocalPort(process: Process, localPort: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) {
                throw IllegalStateException("SSH tunnel process exited with code ${process.exitValue()}")
            }
            if (runCatching {
                Socket("127.0.0.1", localPort).use {}
            }.onFailure {
                it.rethrowIfFatal()
            }.isSuccess) {
                waitForStableProcess(process, localPort)
                return
            }
            Thread.sleep(50)
        }
        throw IllegalStateException("SSH tunnel did not open local port $localPort within timeout")
    }

    private fun waitForStableProcess(process: Process, localPort: Int) {
        repeat(5) {
            Thread.sleep(50)
            if (!process.isAlive) {
                throw IllegalStateException("SSH tunnel process exited after opening local port $localPort with code ${process.exitValue()}")
            }
        }
    }
}

private class ProcessSshTunnel(
    private val process: Process,
    override val localPort: Int,
) : SshTunnel {
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

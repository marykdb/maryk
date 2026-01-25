package maryk.datastore.remote

import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

actual fun defaultSshTunnelFactory(): SshTunnelFactory? = ProcessSshTunnelFactory

private object ProcessSshTunnelFactory : SshTunnelFactory {
    override fun open(config: RemoteSshConfig, target: SshTarget): SshTunnel {
        val localPort = config.localPort?.takeIf { it > 0 } ?: allocateLocalPort()
        val command = buildCommand(config, target, localPort)
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        drainOutput(process.inputStream)

        return ProcessSshTunnel(process, localPort)
    }

    private fun allocateLocalPort(): Int = ServerSocket(0).use { it.localPort }

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
            runCatching { stream.readBytes() }
        }.apply {
            isDaemon = true
            start()
        }
    }
}

private class ProcessSshTunnel(
    private val process: Process,
    override val localPort: Int,
) : SshTunnel {
    override fun close() {
        process.destroy()
        process.waitFor(3, TimeUnit.SECONDS)
        if (process.isAlive) {
            process.destroyForcibly()
        }
    }
}

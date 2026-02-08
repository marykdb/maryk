@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package maryk.datastore.remote

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.INADDR_ANY
import platform.posix.SOCK_STREAM
import platform.posix.SIGKILL
import platform.posix.SIGTERM
import platform.posix.WNOHANG
import platform.posix._exit
import platform.posix.bind
import platform.posix.close
import platform.posix.errno
import platform.posix.execvp
import platform.posix.fork
import platform.posix.getsockname
import platform.posix.kill
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.strerror
import platform.posix.usleep
import platform.posix.waitpid

actual fun defaultSshTunnelFactory(): SshTunnelFactory? = PosixSshTunnelFactory

private object PosixSshTunnelFactory : SshTunnelFactory {
    override fun open(config: RemoteSshConfig, target: SshTarget): SshTunnel {
        val localPort = config.localPort?.takeIf { it > 0 } ?: allocateLocalPort()
        val command = buildCommand(config, target, localPort)
        val pid = spawnProcess(command)
        return PosixSshTunnel(pid, localPort)
    }

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

    private fun spawnProcess(command: List<String>): Int = memScoped {
        val pid = fork()
        if (pid < 0) {
            throw IllegalStateException("Failed to fork ssh tunnel: ${errnoMessage()}")
        }
        if (pid == 0) {
            val argv = allocArray<CPointerVar<ByteVar>>(command.size + 1)
            command.forEachIndexed { index, arg ->
                argv[index] = arg.cstr.ptr
            }
            argv[command.size] = null
            execvp(command.first(), argv)
            _exit(127)
        }
        pid
    }

    @OptIn(UnsafeNumber::class)
    private fun allocateLocalPort(): Int = memScoped {
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        if (fd < 0) {
            throw IllegalStateException("Failed to open socket: ${errnoMessage()}")
        }
        try {
            val addr = alloc<sockaddr_in>()
            addr.sin_family = AF_INET.convert()
            addr.sin_port = 0u
            addr.sin_addr.s_addr = INADDR_ANY

            if (bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) != 0) {
                throw IllegalStateException("Failed to bind socket: ${errnoMessage()}")
            }

            val len = alloc<socklen_tVar>()
            len.value = sizeOf<sockaddr_in>().toUInt()
            if (getsockname(fd, addr.ptr.reinterpret(), len.ptr) != 0) {
                throw IllegalStateException("Failed to read socket name: ${errnoMessage()}")
            }
            portFromNetwork(addr.sin_port)
        } finally {
            close(fd)
        }
    }
}

private class PosixSshTunnel(
    private val pid: Int,
    override val localPort: Int,
) : SshTunnel {
    override fun close() {
        if (pid <= 0) return
        kill(pid, SIGTERM)
        if (!waitForExit(pid, 20, 50_000u)) {
            kill(pid, SIGKILL)
            waitForExit(pid, 5, 50_000u)
        }
    }
}

private fun waitForExit(pid: Int, attempts: Int, sleepMicros: UInt): Boolean {
    memScoped {
        val status = alloc<IntVar>()
        repeat(attempts) {
            val result = waitpid(pid, status.ptr, WNOHANG)
            if (result == pid || result < 0) return true
            usleep(sleepMicros)
        }
    }
    return false
}

private fun errnoMessage(): String = strerror(errno)?.toKString() ?: "errno $errno"

private fun portFromNetwork(port: UShort): Int {
    val value = port.toInt() and 0xFFFF
    return ((value and 0xFF) shl 8) or ((value ushr 8) and 0xFF)
}

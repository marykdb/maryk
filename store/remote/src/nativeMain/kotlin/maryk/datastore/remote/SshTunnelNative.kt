@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package maryk.datastore.remote

import maryk.datastore.remote.interop.maryk_spawnp
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
import platform.posix.SO_REUSEADDR
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SIGKILL
import platform.posix.SIGTERM
import platform.posix.WNOHANG
import platform.posix.bind
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.getsockname
import platform.posix.kill
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.setsockopt
import platform.posix.strerror
import platform.posix.usleep
import platform.posix.waitpid

actual fun defaultSshTunnelFactory(): SshTunnelFactory? = PosixSshTunnelFactory

private object PosixSshTunnelFactory : SshTunnelFactory {
    override fun open(config: RemoteSshConfig, target: SshTarget): SshTunnel {
        val localPort = config.localPort?.takeIf { it > 0 } ?: allocateLocalPort()
        if (config.localPort != null && !isLocalPortAvailable(localPort)) {
            throw IllegalStateException("SSH local port $localPort is already in use")
        }
        val command = buildCommand(config, target, localPort)
        val pid = spawnProcess(command)
        try {
            waitForLocalPort(pid, localPort)
        } catch (error: Throwable) {
            terminateProcess(pid)
            throw error
        }
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
            "$localPort:${target.host.forSshForwarding()}:${target.port}",
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
        val pid = alloc<IntVar>()
        val argv = allocArray<CPointerVar<ByteVar>>(command.size + 1)
        command.forEachIndexed { index, arg ->
            argv[index] = arg.cstr.ptr
        }
        argv[command.size] = null

        val result = maryk_spawnp(pid.ptr, argv)
        if (result != 0) {
            throw IllegalStateException("Failed to spawn ssh tunnel: ${strerror(result)?.toKString() ?: "errno $result"}")
        }
        pid.value
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

private fun String.forSshForwarding(): String =
    if (':' in this && !startsWith("[")) "[$this]" else this

@OptIn(UnsafeNumber::class)
private fun waitForLocalPort(pid: Int, localPort: Int) {
    repeat(200) {
        if (hasExited(pid)) {
            throw IllegalStateException("SSH tunnel process exited before opening local port $localPort")
        }
        if (canConnectToLocalPort(localPort)) {
            waitForStableProcess(pid, localPort)
            return
        }
        usleep(50_000u)
    }
    throw IllegalStateException("SSH tunnel did not open local port $localPort within timeout")
}

private fun hasExited(pid: Int): Boolean = memScoped {
    val status = alloc<IntVar>()
    waitpid(pid, status.ptr, WNOHANG) == pid
}

@OptIn(UnsafeNumber::class)
private fun isLocalPortAvailable(localPort: Int): Boolean = memScoped {
    val fd = socket(AF_INET, SOCK_STREAM, 0)
    if (fd < 0) return@memScoped false
    try {
        val reuseAddress = alloc<IntVar>()
        reuseAddress.value = 1
        if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, reuseAddress.ptr, sizeOf<IntVar>().convert()) != 0) {
            return@memScoped false
        }
        val addr = alloc<sockaddr_in>()
        addr.sin_family = AF_INET.convert()
        addr.sin_port = portToNetwork(localPort)
        addr.sin_addr.s_addr = INADDR_ANY
        bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) == 0
    } finally {
        close(fd)
    }
}

@OptIn(UnsafeNumber::class)
private fun canConnectToLocalPort(localPort: Int): Boolean = memScoped {
    val fd = socket(AF_INET, SOCK_STREAM, 0)
    if (fd < 0) return@memScoped false
    try {
        val addr = alloc<sockaddr_in>()
        addr.sin_family = AF_INET.convert()
        addr.sin_port = portToNetwork(localPort)
        addr.sin_addr.s_addr = LOOPBACK_NETWORK_ORDER
        connect(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) == 0
    } finally {
        close(fd)
    }
}

@OptIn(UnsafeNumber::class)
private fun waitForStableProcess(pid: Int, localPort: Int) {
    repeat(5) {
        usleep(50_000u)
        if (hasExited(pid)) {
            throw IllegalStateException("SSH tunnel process exited after opening local port $localPort")
        }
    }
}

private class PosixSshTunnel(
    private val pid: Int,
    override val localPort: Int,
) : SshTunnel {
    override fun close() {
        terminateProcess(pid)
    }
}

private fun terminateProcess(pid: Int) {
    if (pid <= 0) return
    if (waitForExit(pid, 1, 0u)) return
    kill(pid, SIGTERM)
    if (!waitForExit(pid, 20, 50_000u)) {
        kill(pid, SIGKILL)
        waitForExit(pid, 5, 50_000u)
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

private const val LOOPBACK_NETWORK_ORDER: UInt = 0x0100007Fu

private fun portFromNetwork(port: UShort): Int {
    val value = port.toInt() and 0xFFFF
    return ((value and 0xFF) shl 8) or ((value ushr 8) and 0xFF)
}

private fun portToNetwork(port: Int): UShort {
    val value = port and 0xFFFF
    return (((value and 0xFF) shl 8) or ((value ushr 8) and 0xFF)).toUShort()
}

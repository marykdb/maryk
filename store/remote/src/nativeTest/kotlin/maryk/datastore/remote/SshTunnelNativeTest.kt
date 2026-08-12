@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package maryk.datastore.remote

import kotlinx.cinterop.toKString
import platform.posix.chmod
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.remove
import platform.posix.rmdir
import platform.posix.setenv
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SshTunnelNativeTest {
    @Test
    fun sshProcessExitFailsBeforeTunnelIsReturned() {
        val directory = "/tmp/maryk-remote-ssh-${Random.nextInt()}"
        val sshPath = "$directory/ssh"
        val originalPath = getenv("PATH")?.toKString().orEmpty()

        check(mkdir(directory, 0x1C0u) == 0)
        maryk.file.File.writeText(sshPath, "#!/bin/sh\nexit 73\n")
        check(chmod(sshPath, 0x1EDu) == 0)
        check(setenv("PATH", "$directory:$originalPath", 1) == 0)

        try {
            val exception = assertFailsWith<IllegalStateException> {
                assertNotNull(defaultSshTunnelFactory()).open(
                    RemoteSshConfig(host = "ssh.example"),
                    SshTarget(host = "127.0.0.1", port = 1),
                )
            }

            assertContains(exception.message.orEmpty(), "exited")
        } finally {
            setenv("PATH", originalPath, 1)
            remove(sshPath)
            rmdir(directory)
        }
    }

    @Test
    fun sshProcessTunnelReleasesPortOnClose() {
        val directory = "/tmp/maryk-remote-ssh-${Random.nextInt()}"
        val sshPath = "$directory/ssh"
        val originalPath = getenv("PATH")?.toKString().orEmpty()

        check(mkdir(directory, 0x1C0u) == 0)
        maryk.file.File.writeText(
            sshPath,
            """
            #!/bin/sh
            while [ "${'$'}#" -gt 0 ]; do
                if [ "${'$'}1" = "-L" ]; then
                    forward="${'$'}2"
                    break
                fi
                shift
            done
            port="${'$'}{forward%%:*}"
            exec python3 -c 'import socket, sys; listener = socket.socket(); listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); listener.bind(("127.0.0.1", int(sys.argv[1]))); listener.listen(); [listener.accept()[0].close() for _ in iter(int, 1)]' "${'$'}port"
            """.trimIndent() + "\n",
        )
        check(chmod(sshPath, 0x1EDu) == 0)
        check(setenv("PATH", "$directory:$originalPath", 1) == 0)

        try {
            val factory = assertNotNull(defaultSshTunnelFactory())
            val tunnel = factory.open(
                RemoteSshConfig(host = "ssh.example"),
                SshTarget(host = "127.0.0.1", port = 1),
            )
            tunnel.close()

            factory.open(
                RemoteSshConfig(host = "ssh.example", localPort = tunnel.localPort),
                SshTarget(host = "127.0.0.1", port = 1),
            ).close()
        } finally {
            setenv("PATH", originalPath, 1)
            remove(sshPath)
            rmdir(directory)
        }
    }
}

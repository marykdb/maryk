package maryk.datastore.remote

import kotlin.test.Test
import kotlin.test.assertContains

class SshTunnelJvmTest {
    @Test
    fun sshLocalForwardIsExplicitlyBoundToIpv4Loopback() {
        val factoryClass = Class.forName("maryk.datastore.remote.ProcessSshTunnelFactory")
        val factory = factoryClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        val buildCommand = factoryClass.getDeclaredMethod(
            "buildCommand",
            RemoteSshConfig::class.java,
            SshTarget::class.java,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val command = buildCommand.invoke(
            factory,
            RemoteSshConfig(host = "ssh.example"),
            SshTarget(host = "remote.internal", port = 8210),
            9821,
        ) as List<String>

        assertContains(command, "127.0.0.1:9821:remote.internal:8210")
    }
}

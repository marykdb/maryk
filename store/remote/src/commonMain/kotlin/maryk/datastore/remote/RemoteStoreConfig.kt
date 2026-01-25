package maryk.datastore.remote

import io.ktor.client.HttpClient

/** Configuration for connecting to a remote store. */
data class RemoteStoreConfig(
    val baseUrl: String,
    val ssh: RemoteSshConfig? = null,
    val sshTunnelFactory: SshTunnelFactory? = defaultSshTunnelFactory(),
    val httpClient: HttpClient? = null,
)

/** Optional SSH tunnel configuration for remote store connections. */
data class RemoteSshConfig(
    val host: String,
    val user: String? = null,
    val port: Int = 22,
    val remoteHost: String? = null,
    val remotePort: Int? = null,
    val localPort: Int? = null,
    val identityFile: String? = null,
    val extraArgs: List<String> = emptyList(),
)

/** Target for SSH port forwarding. */
data class SshTarget(
    val host: String,
    val port: Int,
)

/** Active SSH tunnel. */
interface SshTunnel {
    val localPort: Int
    fun close()
}

fun interface SshTunnelFactory {
    fun open(config: RemoteSshConfig, target: SshTarget): SshTunnel
}

expect fun defaultSshTunnelFactory(): SshTunnelFactory?

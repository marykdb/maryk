package maryk.datastore.remote

import io.ktor.client.HttpClient

/** Configuration for connecting to a remote store. */
data class RemoteStoreConfig(
    val baseUrl: String,
    val ssh: RemoteSshConfig? = null,
    val sshTunnelFactory: SshTunnelFactory? = defaultSshTunnelFactory(),
    val httpClient: HttpClient? = null,
    val bearerToken: String? = null,
    val flowRetryPolicy: RemoteFlowRetryPolicy = RemoteFlowRetryPolicy.Disabled,
)

/** Security policy for exposing a remote store server. */
data class RemoteStoreServerConfig(
    val allowInsecureRemoteBinding: Boolean = false,
    val bearerToken: String? = null,
    val flowHeartbeatMillis: Long? = 15_000,
    val authenticator: RemoteStoreAuthenticator? = null,
    val authorizer: RemoteStoreAuthorizer? = null,
)

/** Operational limits for a remote store server. */
data class RemoteStoreServerLimits(
    val maxConcurrentCalls: Int = 64,
    val maxConcurrentFlows: Int = 16,
    val requestBodyReadTimeoutMillis: Long = 15_000,
    val connectionIdleTimeoutSeconds: Int = 45,
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

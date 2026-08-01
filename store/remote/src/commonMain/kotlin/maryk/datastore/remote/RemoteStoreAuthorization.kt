package maryk.datastore.remote

import maryk.core.query.requests.RequestType

/** Authenticated remote caller identity. */
data class RemoteStorePrincipal(
    val id: String,
    val attributes: Map<String, String> = emptyMap(),
)

enum class RemoteStoreOperation {
    Info,
    Execute,
    Flow,
    ProcessUpdate,
    SnapshotVersion,
    MigrationAdmin,
}

data class RemoteStoreAuthorizationRequest(
    val principal: RemoteStorePrincipal,
    val operation: RemoteStoreOperation,
    val requestType: RequestType? = null,
    val modelName: String? = null,
)

/** Converts an HTTP Authorization header into a caller identity. */
fun interface RemoteStoreAuthenticator {
    suspend fun authenticate(authorizationHeader: String?): RemoteStorePrincipal?
}

/** Decides whether a caller may perform one decoded operation. */
fun interface RemoteStoreAuthorizer {
    suspend fun authorize(request: RemoteStoreAuthorizationRequest): Boolean
}

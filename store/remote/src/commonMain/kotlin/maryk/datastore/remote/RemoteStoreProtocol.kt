package maryk.datastore.remote

internal object RemoteStoreProtocol {
    const val contentType = "application/x-maryk-protobuf"
    const val streamContentType = "application/x-maryk-protobuf-stream"

    const val infoPath = "/v1/info"
    const val executePath = "/v1/execute"
    const val flowPath = "/v1/flow"
    const val processUpdatePath = "/v1/process-update"
}

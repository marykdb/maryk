package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.Values
import maryk.datastore.shared.DataStoreStateBackupChunk
import maryk.core.models.DataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Version
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.scan
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.datastore.memory.InMemoryDataStore
import maryk.core.models.migration.StorageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteRestoreSchemaTest {
    @Test
    fun stateEndpointRejectsUnknownNestedFieldsBeforeReplay() = runBoundedIntegrationTest {
        val target = InMemoryDataStore.open(dataModelsById = mapOf(1u to OldRemoteRestoreSchema))
        val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) { remoteStoreModule(target) }.start(wait = false)
        val client = HttpClient(CIO)
        try {
            val port = server.engine.resolvedConnectors().single().port
            val values = NewRemoteRestoreSchema.create {
                objects with mapOf("record" to NewRemoteRestoreLeaf.create { value with "known"; additional with "must survive" })
            }
            @Suppress("UNCHECKED_CAST")
            val record = ValuesWithMetaData<IsRootDataModel>(NewRemoteRestoreSchema.key(values), values as Values<IsRootDataModel>, 10uL, 10uL, false)
            val bytes = encodeStateRestoreBatch(NewRemoteRestoreSchema, DataStoreStateBackupChunk(NewRemoteRestoreSchema.Meta.name, listOf(record)))
            val response = client.post("http://127.0.0.1:$port${RemoteStoreProtocol.stateRestorePath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.stateArchiveContentType)
                header(RemoteStoreProtocol.valueStorageFormatHeader, StorageFormat.CURRENT.toString())
                setBody(bytes)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(target.execute(OldRemoteRestoreSchema.scan(allowTableScan = true)).values.isEmpty())
        } finally { client.close(); server.stop(100, 500); target.close() }
    }

    @Test
    fun restoreEndpointRejectsUnknownNestedFieldsBeforeReplay() = runBoundedIntegrationTest {
        val target = InMemoryDataStore.open(dataModelsById = mapOf(1u to OldRemoteRestoreSchema), keepAllVersions = true)
        val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) { remoteStoreModule(target) }.start(wait = false)
        val client = HttpClient(CIO)
        try {
            val port = server.engine.resolvedConnectors().single().port
            val values = NewRemoteRestoreSchema.create {
                objects with mapOf("record" to NewRemoteRestoreLeaf.create { value with "known"; additional with "must survive" })
            }
            val record = DataObjectVersionedChange<IsRootDataModel>(NewRemoteRestoreSchema.key(values), changes = listOf(
                VersionedChanges(10uL, listOf(ObjectCreate) + values.toChanges()),
            ))
            val update = UpdateResponse(NewRemoteRestoreSchema, InitialChangesUpdate(11uL, listOf(record)))
            val context = RequestContext(DefinitionsContext(mutableMapOf(NewRemoteRestoreSchema.Meta.name to DataModelReference(NewRemoteRestoreSchema))), NewRemoteRestoreSchema)
            val payload = RemoteStoreCodec.encode(RemoteRestoreVersion.Serializer, RemoteRestoreVersion(listOf(update)), context)
            val response = client.post("http://127.0.0.1:$port${RemoteStoreProtocol.restoreVersionPath}") {
                header(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                header(RemoteStoreProtocol.valueStorageFormatHeader, StorageFormat.CURRENT.toString())
                setBody(payload)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(target.execute(OldRemoteRestoreSchema.scan(allowTableScan = true)).values.isEmpty())
        } finally {
            client.close()
            server.stop(500, 500)
            target.close()
        }
    }
}

private object OldRemoteRestoreLeaf : DataModel<OldRemoteRestoreLeaf>() {
    val value by string(1u)
}
private object NewRemoteRestoreLeaf : DataModel<NewRemoteRestoreLeaf>() {
    val value by string(1u)
    val additional by string(2u, required = false)
}
private object OldRemoteRestoreSchema : RootDataModel<OldRemoteRestoreSchema>(name = "RemoteRestoreSchema") {
    val objects by map(1u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { OldRemoteRestoreLeaf }))
}
private object NewRemoteRestoreSchema : RootDataModel<NewRemoteRestoreSchema>(name = "RemoteRestoreSchema", version = Version(1, 1)) {
    val objects by map(1u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { NewRemoteRestoreLeaf }))
}

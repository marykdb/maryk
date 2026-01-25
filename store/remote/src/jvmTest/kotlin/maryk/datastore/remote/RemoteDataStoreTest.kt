package maryk.datastore.remote

import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.memory.InMemoryDataStore
import maryk.test.models.SimpleMarykModel

class RemoteDataStoreTest {
    @Test
    fun remoteExecuteAndFlow() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        val info = RemoteStoreInfo(
            definitions = RemoteDataStore.collectDefinitions(store.dataModelsById.values),
            modelIds = store.dataModelsById.map { (id, model) ->
                RemoteStoreModelId(id = id, name = model.Meta.name)
            },
            keepAllVersions = store.keepAllVersions,
            supportsFuzzyQualifierFiltering = store.supportsFuzzyQualifierFiltering,
            supportsSubReferenceFiltering = store.supportsSubReferenceFiltering,
        )
        val infoBytes = RemoteStoreCodec.encode(RemoteStoreInfo.Serializer, info, DefinitionsConversionContext())
        RemoteStoreCodec.decode(RemoteStoreInfo.Serializer, infoBytes, DefinitionsConversionContext())
        val port = ServerSocket(0).use { it.localPort }
        val engine = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)

        val remote = RemoteDataStore.connect(
            RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port")
        )

        try {
            val values = SimpleMarykModel.create {
                value with "haha"
            }
            val addResponse: AddResponse<SimpleMarykModel> = remote.execute(SimpleMarykModel.add(values))
            val status = addResponse.statuses.firstOrNull()
            assertNotNull(status)
            val addSuccess = status as? AddSuccess<SimpleMarykModel>
            assertNotNull(addSuccess)
            assertTrue(addSuccess.key.bytes.isNotEmpty())

            val getResponse: ValuesResponse<SimpleMarykModel> =
                remote.execute(SimpleMarykModel.get(addSuccess.key))
            val fetched = getResponse.values.firstOrNull()
            assertNotNull(fetched)
            assertEquals("haha", fetched.values { value })

            val flowRequest = SimpleMarykModel.get(addSuccess.key)
            val initialUpdate = remote.executeFlow(flowRequest).first()
            assertTrue(initialUpdate is InitialValuesUpdate<*>)
        } finally {
            remote.close()
            engine.stop(500, 500)
            store.close()
        }
    }
}

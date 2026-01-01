package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.RocksDbStoreConnection
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.IsResponse
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChangesCommandTest {
    private val environment = object : CliEnvironment {
        override fun resolveDirectory(path: String): DirectoryResolution = DirectoryResolution.Success(path)
    }

    @Test
    fun returnsChangesYaml() {
        val values = SimpleMarykModel.create {
            value with "hello"
        }
        val key = SimpleMarykModel.key(values)

        var captured: GetChangesRequest<IsRootDataModel>? = null
        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                val getRequest = request as GetChangesRequest<DM>
                captured = getRequest as GetChangesRequest<IsRootDataModel>
                val changes = listOf(
                    VersionedChanges(
                        version = 1uL,
                        changes = listOf(ObjectCreate, Change(SimpleMarykModel { value::ref } with "hello"))
                    )
                )
                val response = ChangesResponse(
                    dataModel = getRequest.dataModel,
                    changes = listOf(
                        maryk.core.query.changes.DataObjectVersionedChange(
                            key = key,
                            changes = changes,
                        )
                    )
                )
                return response as RP
            }
        }
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = ChangesCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", key.toString()),
        )

        val request = assertNotNull(captured)
        assertEquals(key, request.keys.single())
        assertTrue(result.lines.any { it.contains("----- Changes -----") })
        assertTrue(result.lines.any { it.contains("changes:") })
    }
}

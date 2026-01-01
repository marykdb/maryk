package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.RocksDbStoreConnection
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UndeleteCommandTest {
    private val environment = object : CliEnvironment {
        override fun resolveDirectory(path: String): DirectoryResolution = DirectoryResolution.Success(path)
    }

    @Test
    fun sendsUndeleteChange() {
        val values = SimpleMarykModel.create { value with "hello" }
        val key = SimpleMarykModel.key(values)

        var captured: ChangeRequest<IsRootDataModel>? = null
        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                captured = request as ChangeRequest<IsRootDataModel>
                val response = ChangeResponse(
                    dataModel = request.dataModel,
                    statuses = listOf(ChangeSuccess<IsRootDataModel>(version = 2uL, changes = null)),
                )
                return response as RP
            }
        }
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = UndeleteCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", key.toString()),
        )

        val request = assertNotNull(captured)
        val change = request.objects.single().changes.single() as ObjectSoftDeleteChange
        assertEquals(false, change.isDeleted)
        assertTrue(result.lines.first().startsWith("Restored"))
    }
}

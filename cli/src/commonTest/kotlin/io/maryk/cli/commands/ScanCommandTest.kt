package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.InteractionResult
import io.maryk.cli.RocksDbStoreConnection
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.values.Values
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScanCommandTest {
    private val environment = object : CliEnvironment {
        override fun resolveDirectory(path: String): DirectoryResolution = DirectoryResolution.Success(path)
    }

    @Test
    fun errorsWhenNotConnected() {
        val state = CliState()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("Not connected"))
    }

    @Test
    fun showsUsageWhenArgumentsMissing() {
        val state = connectedState()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            emptyList(),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().startsWith("Usage: scan"))
    }

    @Test
    fun errorsOnUnknownOption() {
        val state = connectedState()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", "--nope"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.single().contains("Unknown option"))
    }

    @Test
    fun errorsWhenLimitIsZero() {
        val state = connectedState()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", "--limit", "0"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.single().contains("`--limit` must be greater than 0."))
    }

    @Test
    fun errorsWhenLimitExceedsRequestMaximum() {
        val state = connectedState()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", "--limit", "100001"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.single().contains("`--limit` must be at most 100000."))
    }

    @Test
    fun errorsWhenMaxCharsTooLow() {
        val state = connectedState()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", "--max-chars", "10"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.single().contains("`--max-chars` must be at least 20."))
    }

    @Test
    fun interactiveShowReportsInvalidReference() {
        val state = connectedStateWithScanRows()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel"),
        )

        assertFalse(result.isError)
        val interaction = state.currentInteraction
        assertTrue(interaction != null)

        val outcome = interaction.onInput("show missing")
        val stay = assertIs<InteractionResult.Stay>(outcome)
        assertTrue(stay.lines.single().startsWith("Show failed:"))
    }

    @Test
    fun interactiveShowKeepsLastValidDisplayOnFailure() {
        val state = connectedStateWithScanRows()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel"),
        )

        assertFalse(result.isError)
        val interaction = state.currentInteraction
        assertTrue(interaction != null)

        val first = assertIs<InteractionResult.Stay>(interaction.onInput("show value"))
        assertTrue(first.lines.single().startsWith("Display fields updated."))
        assertTrue(interaction.promptLines().any { it.contains("Show: value") })

        val failure = assertIs<InteractionResult.Stay>(interaction.onInput("show missing"))
        assertTrue(failure.lines.single().startsWith("Show failed:"))
        assertTrue(interaction.promptLines().any { it.contains("Show: value") })
    }

    @Test
    fun scanStartupReportsDataStoreFailure() {
        val state = connectedStateWithFailingScan()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel"),
        )

        assertFalse(result.isError)
        val interaction = state.currentInteraction
        assertTrue(interaction != null)
        assertTrue(interaction.promptLines().any { it.startsWith("Scan failed: boom") })
    }

    @Test
    fun interactiveUndeleteReportsDataStoreFailure() {
        val state = connectedStateWithFailingUndelete()

        val result = ScanCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel"),
        )

        assertFalse(result.isError)
        val interaction = state.currentInteraction
        assertTrue(interaction != null)

        val outcome = interaction.onInput("undelete")
        val stay = assertIs<InteractionResult.Stay>(outcome)
        assertEquals(listOf("Restore failed: boom"), stay.lines)
    }

    private fun connectedState(): CliState {
        val store = FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        return CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }
    }

    private fun connectedStateWithScanRows(): CliState {
        val values = SimpleMarykModel.create {
            value with "hello"
        }
        val key = SimpleMarykModel.key(values)
        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                val scanRequest = request as ScanRequest<DM>
                val response = ValuesResponse(
                    dataModel = scanRequest.dataModel,
                    values = listOf(
                        ValuesWithMetaData(
                            key = key as Key<DM>,
                            values = values as Values<DM>,
                            firstVersion = 1uL,
                            lastVersion = 1uL,
                            isDeleted = false,
                        ),
                    ),
                )
                return response as RP
            }
        }
        return CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }
    }

    private fun connectedStateWithFailingScan(): CliState {
        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                throw IllegalStateException("boom")
            }
        }
        return CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }
    }

    private fun connectedStateWithFailingUndelete(): CliState {
        val values = SimpleMarykModel.create {
            value with "hello"
        }
        val key = SimpleMarykModel.key(values)
        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                if (request is ScanRequest<*>) {
                    val response = ValuesResponse(
                        dataModel = request.dataModel,
                        values = listOf(
                            ValuesWithMetaData(
                                key = key as Key<DM>,
                                values = values as Values<DM>,
                                firstVersion = 1uL,
                                lastVersion = 1uL,
                                isDeleted = true,
                            ),
                        ),
                    )
                    return response as RP
                }
                throw IllegalStateException("boom")
            }
        }
        return CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }
    }
}

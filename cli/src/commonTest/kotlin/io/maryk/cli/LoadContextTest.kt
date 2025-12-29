package io.maryk.cli

import io.maryk.cli.commands.FakeDataStore
import maryk.core.models.IsRootDataModel
import maryk.core.models.asValues
import maryk.core.models.key
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.file.File
import maryk.test.models.SimpleMarykModel
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoadContextTest {
    @Test
    fun loadsYamlAndAppliesChange() {
        val values = SimpleMarykModel.create {
            value with "updated"
        }
        val key = SimpleMarykModel.key(values)
        val yaml = buildString {
            val writer = YamlWriter { append(it) }
            SimpleMarykModel.Serializer.writeJson(values, writer)
        }

        val path = "build/tmp/load-context-test.yaml"
        File.writeText(path, yaml)

        var captured: ChangeRequest<IsRootDataModel>? = null
        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                @Suppress("UNCHECKED_CAST")
                captured = request as ChangeRequest<IsRootDataModel>
                val response = ChangeResponse(
                    dataModel = request.dataModel,
                    statuses = listOf(ChangeSuccess<IsRootDataModel>(version = 1uL, changes = null)),
                )
                @Suppress("UNCHECKED_CAST")
                return response as RP
            }
        }

        val context = LoadContext(
            label = "SimpleMarykModel ${key.toString()}",
            dataModel = SimpleMarykModel,
            key = key,
            dataStore = store,
        )

        val message = context.load(path, SaveFormat.YAML)

        assertTrue(message.contains("Loaded"))
        val request = assertNotNull(captured)
        assertEquals(key, request.objects.single().key)
        assertTrue(request.objects.single().changes.isNotEmpty())
    }

    @Test
    fun usesMetaVersionGuardWhenPresent() {
        val values = SimpleMarykModel.create {
            value with "meta"
        }
        val key = SimpleMarykModel.key(values)
        val meta = ValuesWithMetaData(
            key = key,
            values = values,
            firstVersion = 1uL,
            lastVersion = 3uL,
            isDeleted = false,
        )

        val requestContext = RequestContext(
            DefinitionsContext(mutableMapOf(SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel))),
            dataModel = SimpleMarykModel,
        )
        val metaValues = ValuesWithMetaData.asValues(meta, requestContext)
        val yaml = buildString {
            val writer = YamlWriter { append(it) }
            ValuesWithMetaData.Serializer.writeJson(metaValues, writer, requestContext)
        }

        val path = "build/tmp/load-context-meta.yaml"
        File.writeText(path, yaml)

        var captured: ChangeRequest<IsRootDataModel>? = null
        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                @Suppress("UNCHECKED_CAST")
                captured = request as ChangeRequest<IsRootDataModel>
                val response = ChangeResponse(
                    dataModel = request.dataModel,
                    statuses = listOf(ChangeSuccess<IsRootDataModel>(version = 4uL, changes = null)),
                )
                @Suppress("UNCHECKED_CAST")
                return response as RP
            }
        }

        val context = LoadContext(
            label = "SimpleMarykModel ${key.toString()}",
            dataModel = SimpleMarykModel,
            key = key,
            dataStore = store,
        )

        val message = context.load(path, SaveFormat.YAML, useMeta = true)

        assertTrue(message.contains("Loaded"))
        val request = assertNotNull(captured)
        assertEquals(meta.lastVersion, request.objects.single().lastVersion)
    }
}

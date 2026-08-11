package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.models.DataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.models.definitions.DataModelDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.fixedBytes
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.UUIDv4Key
import maryk.core.properties.definitions.index.UUIDv7Key
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.scanMaxRequest
import maryk.test.requests.scanOrdersRequest
import maryk.test.requests.scanRequest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.expect

private object CursorUuid4Model : RootDataModel<CursorUuid4Model>(
    name = "CursorLayoutModel",
    keyDefinition = { UUIDv4Key },
)

private object CursorUuid7Model : RootDataModel<CursorUuid7Model>(
    name = "CursorLayoutModel",
    keyDefinition = { UUIDv7Key },
)

private object CursorSameLayoutA : RootDataModel<CursorSameLayoutA>(
    name = "CursorSameLayoutModel",
    keyDefinition = { UUIDv4Key },
) {
    val label by string(index = 1u, maxSize = 20u, default = "first")
}

private object CursorSameLayoutB : RootDataModel<CursorSameLayoutB>(
    name = "CursorSameLayoutModel",
    keyDefinition = { UUIDv4Key },
) {
    val label by string(index = 1u, maxSize = 40u, default = "second")
}

private object CursorDeclarationOrderA : RootDataModel<CursorDeclarationOrderA>(
    name = "CursorDeclarationOrderModel",
    keyDefinition = { CursorDeclarationOrderA.identifier.ref() },
) {
    val identifier by fixedBytes(index = 1u, byteSize = 4, final = true)
    val label by string(index = 2u)
}

private object CursorDeclarationOrderB : RootDataModel<CursorDeclarationOrderB>(
    name = "CursorDeclarationOrderModel",
    keyDefinition = { CursorDeclarationOrderB.identifier.ref() },
) {
    val label by string(index = 2u)
    val identifier by fixedBytes(index = 1u, byteSize = 4, final = true)
}

private object CursorNestedSignedModel : DataModel<CursorNestedSignedModel>(
    meta = { DataModelDefinition(name = "CursorNestedModel") },
) {
    val value by number(index = 1u, type = SInt32)
}

private object CursorNestedUnsignedModel : DataModel<CursorNestedUnsignedModel>(
    meta = { DataModelDefinition(name = "CursorNestedModel") },
) {
    val value by number(index = 1u, type = UInt32)
}

private object CursorNestedIndexA : RootDataModel<CursorNestedIndexA>(
    name = "CursorNestedIndexModel",
    indexes = {
        listOf(CursorNestedIndexA { nested { value::ref } })
    },
) {
    val nested by embed(index = 1u, dataModel = { CursorNestedSignedModel })
}

private object CursorNestedIndexB : RootDataModel<CursorNestedIndexB>(
    name = "CursorNestedIndexModel",
    indexes = {
        listOf(CursorNestedIndexB { nested { value::ref } })
    },
) {
    val nested by embed(index = 1u, dataModel = { CursorNestedUnsignedModel })
}

private object CursorIndexOrderA : RootDataModel<CursorIndexOrderA>(
    name = "CursorIndexOrderModel",
    indexes = {
        listOf(
            CursorIndexOrderA { first::ref },
            CursorIndexOrderA { second::ref },
        )
    },
) {
    val first by number(index = 1u, type = SInt32)
    val second by string(index = 2u)
}

private object CursorIndexOrderB : RootDataModel<CursorIndexOrderB>(
    name = "CursorIndexOrderModel",
    indexes = {
        listOf(
            CursorIndexOrderB { second::ref },
            CursorIndexOrderB { first::ref },
        )
    },
) {
    val first by number(index = 1u, type = SInt32)
    val second by string(index = 2u)
}

private class DelegatingCursorModel(
    delegate: IsRootDataModel,
) : IsRootDataModel by delegate

private object CursorFanOutModel : RootDataModel<CursorFanOutModel>(
    indexes = {
        listOf(
            CursorFanOutModel { setValues.refToAny() },
            CursorFanOutModel { mapValues.refToAnyKey() },
        )
    },
) {
    val setValues by set(
        index = 1u,
        valueDefinition = StringDefinition(),
    )
    val mapValues by map(
        index = 2u,
        keyDefinition = StringDefinition(),
        valueDefinition = StringDefinition(),
    )
}

private object CursorAnyOfFanOutModel : RootDataModel<CursorAnyOfFanOutModel>(
    indexes = {
        listOf(
            AnyOf(
                CursorAnyOfFanOutModel { setValues.refToAny() },
                CursorAnyOfFanOutModel { mapValues.refToAnyKey() },
            )
        )
    },
) {
    val setValues by set(
        index = 1u,
        valueDefinition = StringDefinition(),
    )
    val mapValues by map(
        index = 2u,
        keyDefinition = StringDefinition(),
        valueDefinition = StringDefinition(),
    )
}

private object CursorFanOutLayoutA : RootDataModel<CursorFanOutLayoutA>(
    name = "CursorFanOutLayoutModel",
    indexes = { listOf(CursorFanOutLayoutA { values.refToAny() }) },
) {
    val values by set(
        index = 1u,
        valueDefinition = NumberDefinition(type = SInt32),
    )
}

private object CursorFanOutLayoutB : RootDataModel<CursorFanOutLayoutB>(
    name = "CursorFanOutLayoutModel",
    indexes = { listOf(CursorFanOutLayoutB { values.refToAny() }) },
) {
    val values by set(
        index = 1u,
        valueDefinition = NumberDefinition(type = UInt32),
    )
}

class ScanRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
    ))

    @Test
    fun rejectLimitAboveMaximum() {
        assertFailsWith<RequestException> {
            SimpleMarykModel.scan(limit = MAX_SCAN_LIMIT + 1u)
        }
    }

    @Test
    fun rejectZeroLimit() {
        assertFailsWith<RequestException> {
            SimpleMarykModel.scan(limit = 0u)
        }
    }

    @Test
    fun cursorRoundTripsAndIsBoundToQuery() {
        val base = scanMaxRequest.copy(startKey = null, includeStart = true)
        val key = SimpleMarykModel.key("Zk6m4QpZQegUg5s13JVYlQ")
        val orderKey = byteArrayOf(1, 2, 3, 4)
        val cursor = base.createCursor(key, orderKey)
        val request = base.copy(cursor = cursor)

        val continuation = assertNotNull(request.resolveCursor())
        assertContentEquals(key.bytes, continuation.key.bytes)
        assertContentEquals(orderKey, continuation.orderKey?.bytes)
        checkProtoBufConversion(request, ScanRequest, { this.context })
        checkJsonConversion(request, ScanRequest, { this.context })
        checkYamlConversion(request, ScanRequest, { this.context })

        assertFailsWith<RequestException> {
            request.copy(order = scanOrdersRequest.order).resolveCursor()
        }
        assertFailsWith<RequestException> {
            request.copy(startKey = key)
        }
    }

    @Test
    fun cursorIsRejectedWhenSameNamedModelKeyLayoutChanges() {
        val originalRequest = CursorUuid4Model.scan()
        val cursor = originalRequest.createCursor(
            CursorUuid4Model.key(ByteArray(CursorUuid4Model.Meta.keyByteSize)),
            orderKey = null,
        )
        val migratedRequest = CursorUuid7Model.scan(cursor = cursor)

        assertFailsWith<RequestException> {
            migratedRequest.resolveCursor()
        }
    }

    @Test
    fun cursorIsAcceptedAcrossIndependentModelsWithSameScanLayout() {
        val originalRequest = CursorSameLayoutA.scan()
        val key = CursorSameLayoutA.key(ByteArray(CursorSameLayoutA.Meta.keyByteSize))
        val cursor = originalRequest.createCursor(key, orderKey = null)
        val continuation = assertNotNull(CursorSameLayoutB.scan(cursor = cursor).resolveCursor())

        assertContentEquals(key.bytes, continuation.key.bytes)
    }

    @Test
    fun cursorIsAcceptedWhenUnrelatedPropertiesAreReordered() {
        val originalRequest = CursorDeclarationOrderA.scan()
        val key = CursorDeclarationOrderA.key(byteArrayOf(1, 2, 3, 4))
        val cursor = originalRequest.createCursor(key, orderKey = null)
        val continuation = assertNotNull(CursorDeclarationOrderB.scan(cursor = cursor).resolveCursor())

        assertContentEquals(key.bytes, continuation.key.bytes)
    }

    @Test
    fun cursorIsRejectedWhenNestedSecondaryIndexLeafEncodingChanges() {
        val originalRequest = CursorNestedIndexA.scan()
        val cursor = originalRequest.createCursor(
            CursorNestedIndexA.key(ByteArray(CursorNestedIndexA.Meta.keyByteSize)),
            orderKey = byteArrayOf(1, 2, 3, 4),
        )

        assertFailsWith<RequestException> {
            CursorNestedIndexB.scan(cursor = cursor).resolveCursor()
        }
    }

    @Test
    fun cursorIsAcceptedWhenSecondaryIndexesAreReordered() {
        val originalRequest = CursorIndexOrderA.scan()
        val key = CursorIndexOrderA.key(ByteArray(CursorIndexOrderA.Meta.keyByteSize))
        val cursor = originalRequest.createCursor(key, orderKey = null)
        val continuation = assertNotNull(CursorIndexOrderB.scan(cursor = cursor).resolveCursor())

        assertContentEquals(key.bytes, continuation.key.bytes)
    }

    @Test
    fun cursorSupportsGenericRootDataModelImplementations() {
        val model = DelegatingCursorModel(CursorSameLayoutA)
        val request = model.scan()
        val key = model.key(ByteArray(model.Meta.keyByteSize))
        val cursor = request.createCursor(key, orderKey = null)
        val continuation = assertNotNull(request.copy(cursor = cursor).resolveCursor())

        assertContentEquals(key.bytes, continuation.key.bytes)
    }

    @Test
    fun cursorSupportsSetValueAndMapKeyFanOutIndexes() {
        val request = CursorFanOutModel.scan()
        val key = CursorFanOutModel.key(ByteArray(CursorFanOutModel.Meta.keyByteSize))
        val cursor = request.createCursor(key, orderKey = null)
        val continuation = assertNotNull(request.copy(cursor = cursor).resolveCursor())

        assertContentEquals(key.bytes, continuation.key.bytes)
    }

    @Test
    fun cursorSupportsFanOutReferencesInsideAnyOf() {
        val request = CursorAnyOfFanOutModel.scan()
        val key = CursorAnyOfFanOutModel.key(ByteArray(CursorAnyOfFanOutModel.Meta.keyByteSize))
        val cursor = request.createCursor(key, orderKey = null)
        val continuation = assertNotNull(request.copy(cursor = cursor).resolveCursor())

        assertContentEquals(key.bytes, continuation.key.bytes)
    }

    @Test
    fun cursorRejectsChangedSetFanOutValueEncoding() {
        val originalRequest = CursorFanOutLayoutA.scan()
        val cursor = originalRequest.createCursor(
            CursorFanOutLayoutA.key(ByteArray(CursorFanOutLayoutA.Meta.keyByteSize)),
            orderKey = null,
        )

        assertFailsWith<RequestException> {
            CursorFanOutLayoutB.scan(cursor = cursor).resolveCursor()
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(scanRequest, ScanRequest, { this.context })
        checkProtoBufConversion(scanMaxRequest, ScanRequest, { this.context })
        checkProtoBufConversion(scanRequest.copy(allowTableScan = true), ScanRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(scanRequest, ScanRequest, { this.context })
        checkJsonConversion(scanMaxRequest, ScanRequest, { this.context })
        checkJsonConversion(scanOrdersRequest, ScanRequest, { this.context })
        assertTrue(
            checkJsonConversion(scanRequest.copy(allowTableScan = true), ScanRequest, { this.context }).contains("allowTableScan")
        )
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            from: SimpleMarykModel
            filterSoftDeleted: true
            limit: 100
            includeStart: true
            allowTableScan: false

            """.trimIndent()
        ) {
            checkYamlConversion(scanRequest, ScanRequest, { this.context })
        }

        expect(
            """
            from: SimpleMarykModel
            startKey: Zk6m4QpZQegUg5s13JVYlQ
            select:
            - value
            where: !Exists value
            toVersion: 2345
            filterSoftDeleted: true
            aggregations:
              totalValues: !ValueCount
                of: value
            order: value
            limit: 200
            includeStart: false
            allowTableScan: false

            """.trimIndent()
        ) {
            checkYamlConversion(scanMaxRequest, ScanRequest, { this.context })
        }

        expect(
            """
            from: SimpleMarykModel
            startKey: Zk6m4QpZQegUg5s13JVYlQ
            select:
            - value
            filterSoftDeleted: true
            order:
            - value
            - !Desc value
            limit: 100
            includeStart: true
            allowTableScan: false

            """.trimIndent()
        ) {
            checkYamlConversion(scanOrdersRequest, ScanRequest, { this.context })
        }

        assertTrue(
            checkYamlConversion(scanRequest.copy(allowTableScan = true), ScanRequest, { this.context }).contains("allowTableScan: true")
        )
    }
}

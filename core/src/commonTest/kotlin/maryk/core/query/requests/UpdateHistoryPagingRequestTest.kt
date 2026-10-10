package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.models.graph
import maryk.core.models.key
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.fixedBytes
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Bytes
import maryk.core.query.RequestContext
import maryk.core.query.filters.Exists
import maryk.core.query.responses.UpdatesResponse
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UpdateHistoryPagingRequestTest {
    private val key = SimpleMarykModel.key("WWurg6ysTsozoMei_SurOw")
    private val otherKey = SimpleMarykModel.key("awfbjYrVQ-cdXblfQKV10A")
    private val context = RequestContext(
        mapOf(SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)), dataModel = SimpleMarykModel,
    )

    @Test
    fun getHistoryAndItsCursorRoundTrip() {
        val initial = SimpleMarykModel.getUpdateHistory(
            key, where = Exists(SimpleMarykModel.ref { value }), limit = 25u,
            fromVersion = 10uL, toVersion = 1000uL,
            select = SimpleMarykModel.graph { listOf(value) }, filterSoftDeleted = false,
        )
        for (request in listOf(initial, initial.copy(cursor = initial.createHistoryCursor(900uL, 100uL, key)))) {
            checkProtoBufConversion(request, GetUpdateHistoryRequest, { context })
            checkJsonConversion(request, GetUpdateHistoryRequest, { context })
            checkYamlConversion(request, GetUpdateHistoryRequest, { context })
        }
    }

    @Test
    fun scanHistoryAndResponseCursorsRoundTripAndCopy() {
        val initial = SimpleMarykModel.scanUpdateHistory(limit = 25u)
        val cursor = initial.createHistoryCursor(900uL, 100uL, key)
        val continued = initial.copy(cursor = cursor)
        checkProtoBufConversion(continued, ScanUpdateHistoryRequest, { context })
        checkJsonConversion(continued, ScanUpdateHistoryRequest, { context })
        checkYamlConversion(continued, ScanUpdateHistoryRequest, { context })
        assertEquals(cursor, continued.copy(limit = 10u).cursor)
        assertNotEquals(initial, continued)

        val response = UpdatesResponse(SimpleMarykModel, emptyList(), null, cursor)
        checkProtoBufConversion(response, UpdatesResponse, { context })
        checkJsonConversion(response, UpdatesResponse, { context })
        checkYamlConversion(response, UpdatesResponse, { context })
        assertEquals(cursor, response.copy(updates = emptyList()).nextCursor)
    }

    @Test
    fun cursorBindsQueryButAllowsDifferentPageSize() {
        val initial = SimpleMarykModel.getUpdateHistory(key, limit = 1u)
        val continued = initial.copy(cursor = initial.createHistoryCursor(900uL, 100uL, key), limit = 20u)
        assertEquals(100uL, assertNotNull(continued.resolveHistoryCursor()).version)
        assertFailsWith<RequestException> { continued.copy(key = otherKey).resolveHistoryCursor() }
        assertFailsWith<RequestException> { continued.copy(fromVersion = 1uL).resolveHistoryCursor() }
        assertFailsWith<RequestException> { continued.copy(toVersion = 999uL).resolveHistoryCursor() }
        assertFailsWith<RequestException> { continued.copy(filterSoftDeleted = false).resolveHistoryCursor() }
        assertFailsWith<RequestException> {
            continued.copy(where = Exists(SimpleMarykModel.ref { value })).resolveHistoryCursor()
        }
        assertFailsWith<RequestException> {
            continued.copy(select = SimpleMarykModel.graph { listOf(value) }).resolveHistoryCursor()
        }
        assertFailsWith<RequestException> {
            SimpleMarykModel.scanUpdateHistory(cursor = continued.cursor).resolveHistoryCursor()
        }
    }

    @Test
    fun getHistoryValidatesBoundsAndLimit() {
        assertFailsWith<RequestException> { SimpleMarykModel.getUpdateHistory(key, limit = 0u) }
        assertFailsWith<RequestException> { SimpleMarykModel.getUpdateHistory(key, limit = MAX_SCAN_LIMIT + 1u) }
        assertFailsWith<RequestException> { SimpleMarykModel.getUpdateHistory(key, fromVersion = 2uL, toVersion = 1uL) }
    }

    @Test
    fun scanHistoryValidatesBoundsAndLimit() {
        assertFailsWith<RequestException> { SimpleMarykModel.scanUpdateHistory(limit = 0u) }
        assertFailsWith<RequestException> { SimpleMarykModel.scanUpdateHistory(limit = MAX_SCAN_LIMIT + 1u) }
        assertFailsWith<RequestException> { SimpleMarykModel.scanUpdateHistory(fromVersion = 2uL, toVersion = 1uL) }
        assertNull(SimpleMarykModel.scanUpdateHistory(fromVersion = 1uL, toVersion = 1uL).cursor)
    }

    @Test
    fun malformedHistoryCursorsAreRejected() {
        val request = SimpleMarykModel.scanUpdateHistory(fromVersion = 10uL, toVersion = 1000uL)
        val bytes = request.createHistoryCursor(900uL, 100uL, key).token.bytes
        val malformed = listOf(
            byteArrayOf(),
            bytes.copyOf(40),
            bytes + byteArrayOf(0),
            bytes.copyOf().also { it[0] = 0 }, // Unknown magic.
            bytes.copyOf().also { it[4] = 2 }, // Unsupported format version.
            bytes.copyOf().also { it[5] = 127 }, // Invalid model-name payload length.
            bytes.copyOf().also { it[41] = 0 }, // Different model name.
            bytes.copyOf().also { it[9] = (it[9].toInt() xor 1).toByte() }, // Different query.
            bytes.copyOf().also { it[17] = (it[17].toInt() xor 1).toByte() }, // Different schema.
            (bytes + byteArrayOf(0)).also { it[8] = (it[8] + 1).toByte() }, // Wrong key size.
        )
        malformed.forEach { tokenBytes ->
            val cursor = UpdateHistoryCursor.Model.create(context) {
                token with Bytes(tokenBytes)
            }.toDataObject()
            assertFailsWith<RequestException> { request.copy(cursor = cursor).resolveHistoryCursor() }
        }
        for ((upper, version) in listOf(90uL to 100uL, 900uL to 9uL, 1001uL to 100uL)) {
            assertFailsWith<RequestException> {
                request.copy(cursor = request.createHistoryCursor(upper, version, key)).resolveHistoryCursor()
            }
        }
    }

    @Test
    fun historyCursorRejectsChangedKeyLayoutWithTheSameModelName() {
        val request = HistoryLayoutA.scanUpdateHistory()
        val cursor = request.createHistoryCursor(900uL, 100uL, HistoryLayoutA.key(byteArrayOf(1, 2, 3, 4)))
        assertFailsWith<RequestException> { HistoryLayoutB.scanUpdateHistory(cursor = cursor).resolveHistoryCursor() }
    }

    @Test
    fun copiesCanExplicitlyClearHistoryContinuation() {
        val initial = SimpleMarykModel.scanUpdateHistory(limit = 25u)
        val cursor = initial.createHistoryCursor(900uL, 100uL, key)
        assertEquals(initial, initial.copy(cursor = cursor).copy(cursor = null))
        val response = UpdatesResponse(SimpleMarykModel, emptyList(), null, cursor)
        assertNull(response.copy(nextCursor = null).nextCursor)
    }
}

private object HistoryLayoutA : RootDataModel<HistoryLayoutA>(
    name = "HistoryLayout", keyDefinition = { HistoryLayoutA.id.ref() },
) {
    val id by fixedBytes(1u, byteSize = 4, final = true)
}

private object HistoryLayoutB : RootDataModel<HistoryLayoutB>(
    name = "HistoryLayout", keyDefinition = { HistoryLayoutB.id.ref() },
) {
    val id by fixedBytes(1u, byteSize = 8, final = true)
}

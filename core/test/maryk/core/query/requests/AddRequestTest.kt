package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.expect

class AddRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
    ))
    private val keyedAddRequest = AddRequest(
        dataModel = SimpleMarykModel,
        objects = addRequest.objects,
        keysForObjects = listOf(
            SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { it.toByte() }),
            SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { (it + 1).toByte() })
        )
    )

    @Test
    fun rejectTooManyObjects() {
        assertFailsWith<RequestException> {
            AddRequest(
                dataModel = SimpleMarykModel,
                objects = List((MAX_REQUEST_BATCH_SIZE + 1u).toInt()) { addRequest.objects.first() }
            )
        }
    }

    @Test
    fun rejectMismatchedPredefinedKeys() {
        assertFailsWith<RequestException> {
            AddRequest(
                dataModel = SimpleMarykModel,
                objects = addRequest.objects,
                keysForObjects = listOf(SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize)))
            )
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(addRequest, AddRequest, { this.context })
        checkProtoBufConversion(keyedAddRequest, AddRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(addRequest, AddRequest, { this.context })
        assertTrue(
            checkJsonConversion(keyedAddRequest, AddRequest, { this.context }).contains("keysForObjects")
        )
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            to: SimpleMarykModel
            objects:
            - value: haha1
            - value: haha2

            """.trimIndent()
        ) {
            checkYamlConversion(addRequest, AddRequest, { this.context })
        }

        assertTrue(
            checkYamlConversion(keyedAddRequest, AddRequest, { this.context }).contains("keysForObjects:")
        )
    }
}

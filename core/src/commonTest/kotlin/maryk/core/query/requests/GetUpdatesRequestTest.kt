package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.getUpdatesMaxRequest
import maryk.test.requests.getUpdatesRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

class GetUpdatesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
    ))

    @Test
    fun rejectTooManyKeys() {
        assertFailsWith<RequestException> {
            GetUpdatesRequest(
                dataModel = SimpleMarykModel,
                keys = List((MAX_REQUEST_BATCH_SIZE + 1u).toInt()) { getUpdatesRequest.keys.first() }
            )
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(getUpdatesRequest, GetUpdatesRequest, { this.context })
        checkProtoBufConversion(getUpdatesMaxRequest, GetUpdatesRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(getUpdatesRequest, GetUpdatesRequest, { this.context })
        checkJsonConversion(getUpdatesMaxRequest, GetUpdatesRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            from: SimpleMarykModel
            keys: [WWurg6ysTsozoMei_SurOw, awfbjYrVQ-cdXblfQKV10A]
            filterSoftDeleted: true
            fromVersion: 0
            maxVersions: 1

            """.trimIndent()
        ) {
            checkYamlConversion(getUpdatesRequest, GetUpdatesRequest, { this.context })
        }

        expect(
            """
            from: SimpleMarykModel
            keys: [WWurg6ysTsozoMei_SurOw, awfbjYrVQ-cdXblfQKV10A]
            select:
            - value
            where: !Exists value
            toVersion: 12345
            filterSoftDeleted: true
            fromVersion: 1234
            maxVersions: 5

            """.trimIndent()
        ) {
            checkYamlConversion(getUpdatesMaxRequest, GetUpdatesRequest, { this.context })
        }
    }
}

package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.getChangesMaxRequest
import maryk.test.requests.getChangesRequest
import kotlin.test.Test
import kotlin.test.expect

class GetChangesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(getChangesRequest, GetChangesRequest, { this.context })
        checkProtoBufConversion(getChangesMaxRequest, GetChangesRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(getChangesRequest, GetChangesRequest, { this.context })
        checkJsonConversion(getChangesMaxRequest, GetChangesRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            from: SimpleMarykModel
            keys: [WWurg6ysTsozoMei/SurOw, awfbjYrVQ+cdXblfQKV10A]
            filterSoftDeleted: true
            fromVersion: 0
            maxVersions: 1

            """.trimIndent()
        ) {
            checkYamlConversion(getChangesRequest, GetChangesRequest, { this.context })
        }

        expect(
            """
            from: SimpleMarykModel
            keys: [WWurg6ysTsozoMei/SurOw, awfbjYrVQ+cdXblfQKV10A]
            select:
            - value
            where: !Exists value
            toVersion: 12345
            filterSoftDeleted: true
            aggregations:
              totalValues: !ValueCount
                of: value
            fromVersion: 1234
            maxVersions: 5

            """.trimIndent()
        ) {
            checkYamlConversion(getChangesMaxRequest, GetChangesRequest, { this.context })
        }

    }
}

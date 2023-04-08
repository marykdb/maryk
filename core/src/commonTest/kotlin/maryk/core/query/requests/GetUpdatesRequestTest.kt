package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.getUpdatesMaxRequest
import maryk.test.requests.getUpdatesRequest
import kotlin.test.Test
import kotlin.test.expect

class GetUpdatesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(getUpdatesRequest, GetChangesRequest, { this.context })
        checkProtoBufConversion(getUpdatesMaxRequest, GetChangesRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(getUpdatesRequest, GetChangesRequest, { this.context })
        checkJsonConversion(getUpdatesMaxRequest, GetChangesRequest, { this.context })
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
            checkYamlConversion(getUpdatesRequest, GetChangesRequest, { this.context })
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
            fromVersion: 1234
            maxVersions: 5

            """.trimIndent()
        ) {
            checkYamlConversion(getUpdatesMaxRequest, GetChangesRequest, { this.context })
        }
    }
}

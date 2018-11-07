package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.getVersionedChangesMaxRequest
import maryk.test.requests.getVersionedChangesRequest
import maryk.test.shouldBe
import kotlin.test.Test

class GetVersionedChangesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(getVersionedChangesRequest, GetVersionedChangesRequest, { this.context })
        checkProtoBufConversion(getVersionedChangesMaxRequest, GetVersionedChangesRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(getVersionedChangesRequest, GetVersionedChangesRequest, { this.context })
        checkJsonConversion(getVersionedChangesMaxRequest, GetVersionedChangesRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(getVersionedChangesRequest, GetVersionedChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        keys: [WWurg6ysTsozoMei/SurOw, awfbjYrVQ+cdXblfQKV10A]
        filterSoftDeleted: true
        fromVersion: 1234
        maxVersions: 1000

        """.trimIndent()

        checkYamlConversion(getVersionedChangesMaxRequest, GetVersionedChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        keys: [WWurg6ysTsozoMei/SurOw, awfbjYrVQ+cdXblfQKV10A]
        select:
        - value
        filter: !Exists value
        order: !Desc value
        toVersion: 12345
        filterSoftDeleted: true
        fromVersion: 1234
        maxVersions: 5

        """.trimIndent()
    }
}

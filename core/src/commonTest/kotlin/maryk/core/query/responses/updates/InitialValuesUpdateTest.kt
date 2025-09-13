package maryk.core.query.responses.updates

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

internal class InitialValuesUpdateTest {
    private val key = SimpleMarykModel.key("0ruQCs38S2QaByYof-IJgA")

    private val initialValues = InitialValuesUpdate(
        version = 1uL,
        values = listOf(
            ValuesWithMetaData(
                key = key,
                firstVersion = 0uL,
                lastVersion = 1uL,
                isDeleted = false,
                values = SimpleMarykModel.create {
                    value with "test value 1"
                }
            )
        )
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.initialValues, InitialValuesUpdate, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.initialValues, InitialValuesUpdate, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            version: 1
            values:
            - key: 0ruQCs38S2QaByYof-IJgA
              values:
                value: test value 1
              firstVersion: 0
              lastVersion: 1
              isDeleted: false

            """.trimIndent()
        ) {
            checkYamlConversion(this.initialValues, InitialValuesUpdate, { this.context })
        }
    }
}

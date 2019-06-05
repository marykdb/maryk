package maryk.core.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsContext
import maryk.core.query.requests.Requests
import maryk.test.models.Option
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.requests.getMaxRequest
import kotlin.test.Test
import kotlin.test.expect

class RootMarykTest {
    private val rootMaryk = RootMaryk(
        listOf(
            TypedValue(
                Operation.Define,
                Definitions(
                    Option,
                    SimpleMarykModel
                )
            ),
            TypedValue(
                Operation.Request,
                Requests(
                    addRequest,
                    getMaxRequest
                )
            )
        )
    )


    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.rootMaryk, RootMaryk, { DefinitionsContext() }, ::compareRootMaryk, true)
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.rootMaryk, RootMaryk, { DefinitionsContext() }, ::compareRootMaryk, true)
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            - !Define
              Option: !EnumDefinition
                cases:
                  1: V1
                  2: [V2, VERSION2]
                  3: [V3, VERSION3]
                reservedIndices: [4]
                reservedNames: [V4]
              SimpleMarykModel: !RootModel
                key: !UUID
                ? 1: value
                : !String
                  required: true
                  final: false
                  unique: false
                  default: haha
                  regEx: ha.*
            - !Request
              - !Add
                to: SimpleMarykModel
                objects:
                - value: haha1
                - value: haha2
              - !Get
                from: SimpleMarykModel
                keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
                select:
                - value
                where: !Exists value
                toVersion: 333
                filterSoftDeleted: true
                aggregations:
                  totalValues: !ValueCount
                    of: value

            """.trimIndent()
        ) {
            checkYamlConversion(this.rootMaryk, RootMaryk, { DefinitionsContext() }, ::compareRootMaryk, true)
        }
    }

    private fun compareRootMaryk(converted: RootMaryk, original: RootMaryk) {
        expect(original.operations.size) { converted.operations.size }

        for ((index, item) in original.operations.withIndex()) {
            val convertedItem = converted.operations[index]

            expect(convertedItem.type) { item.type }

            when (item.type) {
                Operation.Define -> {
                    compareDefinitions(item.value as Definitions, convertedItem.value as Definitions)
                }
                Operation.Request -> {
                    val originalRequests = item.value as Requests
                    val convertedRequests = convertedItem.value as Requests

                    expect(convertedRequests.requests.size) { originalRequests.requests.size }
                    // Skip testing here since model instances are different
                    // Internal conversion is tested in RequestsTest
                }
            }
        }
    }
}

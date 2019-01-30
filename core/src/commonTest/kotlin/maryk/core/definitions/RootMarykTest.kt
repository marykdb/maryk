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
import maryk.test.shouldBe
import kotlin.test.Test

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
        checkYamlConversion(this.rootMaryk, RootMaryk, { DefinitionsContext() }, ::compareRootMaryk, true) shouldBe """
        - !Define
          Option: !EnumDefinition
            values:
              1: V1
              2: V2
              3: V3
          SimpleMarykModel: !RootModel
            key:
            - !UUID
            properties:
              ? 1: value
              : !String
                indexed: false
                required: true
                final: false
                unique: false
                default: haha
                regEx: ha.*
        - !Request
          - !Add
            dataModel: SimpleMarykModel
            objectsToAdd:
            - value: haha1
            - value: haha2
          - !Get
            dataModel: SimpleMarykModel
            keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
            select:
            - value
            filter: !Exists value
            order: !Desc value
            toVersion: 333
            filterSoftDeleted: true

        """.trimIndent()
    }

    private fun compareRootMaryk(converted: RootMaryk, original: RootMaryk) {
        converted.operations.size shouldBe original.operations.size

        for ((index, item) in original.operations.withIndex()) {
            val convertedItem = converted.operations[index]

            item.type shouldBe convertedItem.type

            when(item.type) {
                Operation.Define -> {
                    compareDefinitions(item.value as Definitions, convertedItem.value as Definitions)
                }
                Operation.Request -> {
                    val originalRequests = item.value as Requests
                    val convertedRequests = convertedItem.value as Requests

                    originalRequests.requests.size shouldBe convertedRequests.requests.size
                    // Skip testing here since model instances are different
                    // Internal conversion is tested in RequestsTest
                }
            }
        }
    }
}

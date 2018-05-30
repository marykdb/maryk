package maryk.core.definitions

import maryk.Option
import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelContext
import maryk.core.query.requests.Requests
import maryk.core.query.requests.addRequest
import maryk.core.query.requests.getMaxRequest
import maryk.test.shouldBe
import kotlin.test.Test

class RootMarykTest {
    private val rootMaryk = RootMaryk(
        listOf(
            TypedValue(
                Operation.Define,
                Definitions(
                    Option,
                    SimpleMarykObject
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
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.rootMaryk, RootMaryk, { DataModelContext() }, ::compareRootMaryk, true)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.rootMaryk, RootMaryk, { DataModelContext() }, ::compareRootMaryk, true)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.rootMaryk, RootMaryk, { DataModelContext() }, ::compareRootMaryk, true) shouldBe """
        - !Define
          - !EnumDefinition
            name: Option
            values:
              0: V0
              1: V1
              2: V2
          - !RootModel
            name: SimpleMarykObject
            key:
            - !UUID
            properties:
              ? 0: value
              : !String
                indexed: false
                searchable: true
                required: true
                final: false
                unique: false
                default: haha
                regEx: ha.*
        - !Request
          - !Add
            dataModel: SimpleMarykObject
            objectsToAdd:
            - value: haha1
            - value: haha2
          - !Get
            dataModel: SimpleMarykObject
            keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
            filter: !Exists value
            order: !Desc value
            toVersion: 0x000000000000014d
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

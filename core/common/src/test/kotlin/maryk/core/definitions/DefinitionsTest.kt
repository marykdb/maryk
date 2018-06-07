package maryk.core.definitions

import maryk.EmbeddedMarykObject
import maryk.Option
import maryk.SimpleMarykObject
import maryk.TestMarykObject
import maryk.TestValueObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.objects.DataModel
import maryk.core.objects.compareDataModels
import maryk.core.properties.types.IndexedEnumDefinition
import maryk.core.properties.types.compareEnumDefinitions
import maryk.core.query.DataModelContext
import maryk.test.shouldBe
import kotlin.test.Test

class DefinitionsTest {
    private val definitions = Definitions(
        TestValueObject,
        SimpleMarykObject,
        EmbeddedMarykObject,
        TestMarykObject,
        Option
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.definitions, Definitions, { DataModelContext() }, ::compareDefinitions)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.definitions, Definitions, { DataModelContext() }, ::compareDefinitions)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.definitions, Definitions, { DataModelContext() }, ::compareDefinitions) shouldBe """
        - !ValueModel
          name: TestValueObject
          properties:
            ? 0: int
            : !Number
              indexed: false
              searchable: true
              required: true
              final: false
              unique: false
              type: SInt32
              maxValue: 6
              random: false
            ? 1: dateTime
            : !DateTime
              indexed: false
              searchable: true
              required: true
              final: false
              unique: false
              precision: SECONDS
              fillWithNow: false
            ? 2: bool
            : !Boolean
              indexed: false
              searchable: true
              required: true
              final: false
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
        - !Model
          name: EmbeddedMarykObject
          properties:
            ? 0: value
            : !String
              indexed: false
              searchable: true
              required: true
              final: false
              unique: false
            ? 1: embedded
            : !Embed
              indexed: false
              searchable: true
              required: false
              final: false
              dataModel: EmbeddedMarykObject
            ? 2: marykModel
            : !Embed
              indexed: false
              searchable: true
              required: false
              final: false
              dataModel: TestMarykObject
        - !RootModel
          name: TestMarykObject
          key:
          - !Ref uint
          - !Ref bool
          - !Ref enum
          properties:
            ? 0: string
            : !String
              indexed: false
              searchable: true
              required: true
              final: false
              unique: false
              default: haha
              regEx: ha.*
            ? 1: int
            : !Number
              indexed: false
              searchable: true
              required: true
              final: false
              unique: false
              type: SInt32
              maxValue: 6
              random: false
            ? 2: uint
            : !Number
              indexed: false
              searchable: true
              required: true
              final: true
              unique: false
              type: UInt32
              random: false
            ? 3: double
            : !Number
              indexed: false
              searchable: true
              required: true
              final: false
              unique: false
              type: Float64
              random: false
            ? 4: dateTime
            : !DateTime
              indexed: false
              searchable: true
              required: true
              final: false
              unique: false
              precision: SECONDS
              fillWithNow: false
            ? 5: bool
            : !Boolean
              indexed: false
              searchable: true
              required: true
              final: true
            ? 6: enum
            : !Enum
              indexed: false
              searchable: true
              required: true
              final: true
              unique: false
              name: Option
              values:
                0: V0
                1: V1
                2: V2
              default: V0
            ? 7: list
            : !List
              indexed: false
              searchable: true
              required: false
              final: false
              valueDefinition: !Number
                indexed: false
                searchable: true
                required: true
                final: false
                unique: false
                type: SInt32
                random: false
            ? 8: set
            : !Set
              indexed: false
              searchable: true
              required: false
              final: false
              valueDefinition: !Date
                indexed: false
                searchable: true
                required: true
                final: false
                unique: false
                fillWithNow: false
            ? 9: map
            : !Map
              indexed: false
              searchable: true
              required: false
              final: false
              keyDefinition: !Time
                indexed: false
                searchable: true
                required: true
                final: false
                unique: false
                precision: SECONDS
                fillWithNow: false
              valueDefinition: !String
                indexed: false
                searchable: true
                required: true
                final: false
                unique: false
            ? 10: valueObject
            : !Value
              indexed: false
              searchable: true
              required: false
              final: false
              unique: false
              dataModel: TestValueObject
            ? 11: embeddedObject
            : !Embed
              indexed: false
              searchable: true
              required: false
              final: false
              dataModel: EmbeddedMarykObject
            ? 12: multi
            : !MultiType
              indexed: false
              searchable: true
              required: false
              final: false
              typeEnum: Option
              definitionMap:
                ? 0: V0
                : !String
                  indexed: false
                  searchable: true
                  required: true
                  final: false
                  unique: false
                ? 1: V1
                : !Number
                  indexed: false
                  searchable: true
                  required: true
                  final: false
                  unique: false
                  type: SInt32
                  random: false
                ? 2: V2
                : !Embed
                  indexed: false
                  searchable: true
                  required: true
                  final: false
                  dataModel: EmbeddedMarykObject
            ? 13: reference
            : !Reference
              indexed: false
              searchable: true
              required: false
              final: false
              unique: false
              dataModel: TestMarykObject
            ? 14: listOfString
            : !List
              indexed: false
              searchable: true
              required: false
              final: false
              valueDefinition: !String
                indexed: false
                searchable: true
                required: true
                final: false
                unique: false
            ? 15: selfReference
            : !Reference
              indexed: false
              searchable: true
              required: false
              final: false
              unique: false
              dataModel: TestMarykObject
        - !EnumDefinition
          name: Option
          values:
            0: V0
            1: V1
            2: V2

        """.trimIndent()
    }
}

internal fun compareDefinitions(converted: Definitions, original: Definitions) {
    converted.definitions.size shouldBe original.definitions.size

    for ((index, item) in original.definitions.withIndex()) {
        if (item is DataModel<*, *>) {
            (converted.definitions[index] as? DataModel<*, *>)?.let {
                compareDataModels(it, item)
            } ?: throw AssertionError("Converted Model should be a DataModel")
        } else if (item is IndexedEnumDefinition<*>) {
            (converted.definitions[index] as? IndexedEnumDefinition<*>)?.let {
                compareEnumDefinitions(it, item)
            } ?: throw AssertionError("Converted item should be an EnumDefinition")
        }
    }
}

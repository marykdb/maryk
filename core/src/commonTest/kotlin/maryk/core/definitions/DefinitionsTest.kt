package maryk.core.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.ObjectDataModel
import maryk.core.models.compareDataModels
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.compareEnumDefinitions
import maryk.core.query.DefinitionsConversionContext
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import maryk.test.models.TestValueObject
import maryk.test.shouldBe
import kotlin.test.Test

class DefinitionsTest {
    private val definitions = Definitions(
        Option,
        TestValueObject,
        SimpleMarykModel,
        EmbeddedMarykModel,
        TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.definitions, Definitions, { DefinitionsConversionContext() }, ::compareDefinitions, true)
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.definitions, Definitions, { DefinitionsConversionContext() }, ::compareDefinitions, true)
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.definitions, Definitions, { DefinitionsConversionContext() }, ::compareDefinitions, true) shouldBe """
        Option: !EnumDefinition
          cases:
            1: V1
            2: V2
            3: V3
        TestValueObject: !ValueModel
          ? 1: int
          : !Number
            indexed: false
            required: true
            final: false
            unique: false
            type: SInt32
            maxValue: 6
            random: false
          ? 2: dateTime
          : !DateTime
            indexed: false
            required: true
            final: false
            unique: false
            precision: SECONDS
            fillWithNow: false
          ? 3: bool
          : !Boolean
            indexed: false
            required: true
            final: false
        SimpleMarykModel: !RootModel
          key:
          - !UUID
          ? 1: value
          : !String
            indexed: false
            required: true
            final: false
            unique: false
            default: haha
            regEx: ha.*
        EmbeddedMarykModel: !Model
          ? 1: value
          : !String
            indexed: false
            required: true
            final: false
            unique: false
          ? 2: model
          : !Embed
            indexed: false
            required: false
            final: false
            dataModel: EmbeddedMarykModel
          ? 3: marykModel
          : !Embed
            indexed: false
            required: false
            final: false
            dataModel: TestMarykModel
        TestMarykModel: !RootModel
          key:
          - !Ref uint
          - !Ref bool
          - !Ref enum
          ? 1: string
          : !String
            indexed: false
            required: true
            final: false
            unique: false
            default: haha
            regEx: ha.*
          ? 2: int
          : !Number
            indexed: false
            required: true
            final: false
            unique: false
            type: SInt32
            maxValue: 6
            random: false
          ? 3: uint
          : !Number
            indexed: false
            required: true
            final: true
            unique: false
            type: UInt32
            random: false
          ? 4: double
          : !Number
            indexed: false
            required: true
            final: false
            unique: false
            type: Float64
            random: false
          ? 5: dateTime
          : !DateTime
            indexed: false
            required: true
            final: false
            unique: false
            precision: SECONDS
            fillWithNow: false
          ? 6: bool
          : !Boolean
            indexed: false
            required: true
            final: true
          ? 7: enum
          : !Enum
            indexed: false
            required: true
            final: true
            unique: false
            enum: Option
            default: V1
          ? 8: list
          : !List
            indexed: false
            required: false
            final: false
            valueDefinition: !Number
              indexed: false
              required: true
              final: false
              unique: false
              type: SInt32
              random: false
          ? 9: set
          : !Set
            indexed: false
            required: false
            final: false
            maxSize: 5
            valueDefinition: !Date
              indexed: false
              required: true
              final: false
              unique: false
              maxValue: 2100-12-31
              fillWithNow: false
          ? 10: map
          : !Map
            indexed: false
            required: false
            final: false
            maxSize: 5
            keyDefinition: !Time
              indexed: false
              required: true
              final: false
              unique: false
              precision: SECONDS
              maxValue: '23:00'
              fillWithNow: false
            valueDefinition: !String
              indexed: false
              required: true
              final: false
              unique: false
              maxSize: 10
          ? 11: valueObject
          : !Value
            indexed: false
            required: false
            final: false
            unique: false
            dataModel: TestValueObject
          ? 12: embeddedValues
          : !Embed
            indexed: false
            required: false
            final: false
            dataModel: EmbeddedMarykModel
          ? 13: multi
          : !MultiType
            indexed: false
            required: false
            final: false
            typeEnum: Option
            typeIsFinal: true
            definitionMap:
              ? 1: V1
              : !String
                indexed: false
                required: true
                final: false
                unique: false
              ? 2: V2
              : !Number
                indexed: false
                required: true
                final: false
                unique: false
                type: SInt32
                random: false
              ? 3: V3
              : !Embed
                indexed: false
                required: true
                final: false
                dataModel: EmbeddedMarykModel
          ? 14: reference
          : !Reference
            indexed: false
            required: false
            final: false
            unique: false
            dataModel: TestMarykModel
          ? 15: listOfString
          : !List
            indexed: false
            required: false
            final: false
            maxSize: 6
            valueDefinition: !String
              indexed: false
              required: true
              final: false
              unique: false
              maxSize: 10
          ? 16: selfReference
          : !Reference
            indexed: false
            required: false
            final: false
            unique: false
            dataModel: TestMarykModel
          ? 17: setOfString
          : !Set
            indexed: false
            required: false
            final: false
            maxSize: 6
            valueDefinition: !String
              indexed: false
              required: true
              final: false
              unique: false
              maxSize: 10

        """.trimIndent()
    }
}

internal fun compareDefinitions(converted: Definitions, original: Definitions) {
    converted.definitions.size shouldBe original.definitions.size

    for ((index, item) in original.definitions.withIndex()) {
        if (item is ObjectDataModel<*, *>) {
            (converted.definitions[index] as? ObjectDataModel<*, *>)?.let {
                compareDataModels(it, item)
            } ?: throw AssertionError("Converted Model ${converted.definitions[index]} should be a ObjectDataModel")
        } else if (item is IndexedEnumDefinition<*>) {
            (converted.definitions[index] as? IndexedEnumDefinition<*>)?.let {
                compareEnumDefinitions(it, item)
            } ?: throw AssertionError("Converted item ${converted.definitions[index]} should be an EnumDefinition")
        }
    }
}

package maryk.core.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.IsStorableDataModel
import maryk.core.models.compareDataModels
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.compareEnumDefinitions
import maryk.core.query.DefinitionsConversionContext
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.Option
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum
import maryk.test.models.TestMarykModel
import maryk.test.models.TestValueObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

class DefinitionsTest {
    private val definitions = Definitions(
        Option,
        TestValueObject,
        SimpleMarykModel,
        EmbeddedMarykModel,
        SimpleMarykTypeEnum,
        MarykTypeEnum,
        TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(
            this.definitions,
            Definitions,
            { DefinitionsConversionContext() },
            ::compareDefinitions,
            true
        )
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(
            this.definitions,
            Definitions,
            { DefinitionsConversionContext() },
            ::compareDefinitions,
            true
        )
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            Option: !EnumDefinition
              cases:
                1: V1
                2: [V2, VERSION2]
                3: [V3, VERSION3]
              reservedIndices: [4]
              reservedNames: [V4]
            TestValueObject: !ValueModel
              ? 1: int
              : !Number
                required: true
                final: false
                unique: false
                type: SInt32
                maxValue: 6
              ? 2: dateTime
              : !DateTime
                required: true
                final: false
                unique: false
                precision: SECONDS
              ? 3: bool
              : !Boolean
                required: true
                final: false
            SimpleMarykModel: !RootModel
              version: 1.0
              key: !UUID
              ? 1: value
              : !String
                required: true
                final: false
                unique: false
                default: haha
                regEx: ha.*
            EmbeddedMarykModel: !Model
              reservedIndices: [999]
              reservedNames: [reserved]
              ? 1: value
              : !String
                required: true
                final: false
                unique: false
              ? 2: model
              : !Embed
                required: false
                final: false
                dataModel: EmbeddedMarykModel
              ? 3: marykModel
              : !Embed
                required: false
                final: false
                dataModel: TestMarykModel
            SimpleMarykTypeEnum: !TypeDefinition
              cases:
                ? 1: [S1, Type1]
                : !String
                  required: true
                  final: false
                  unique: false
                  regEx: '[^&]+'
                ? 2: S2
                : !Number
                  required: true
                  final: false
                  unique: false
                  type: SInt16
                ? 3: S3
                : !Embed
                  required: true
                  final: false
                  dataModel: EmbeddedMarykModel
              reservedIndices: [99]
              reservedNames: [O99]
            MarykTypeEnum: !TypeDefinition
              cases:
                ? 1: [T1, Type1]
                : !String
                  required: true
                  final: false
                  unique: false
                  regEx: '[^&]+'
                ? 2: T2
                : !Number
                  required: true
                  final: false
                  unique: false
                  type: SInt32
                  maxValue: 2000
                ? 3: T3
                : !Embed
                  required: true
                  final: false
                  dataModel: EmbeddedMarykModel
                ? 4: T4
                : !List
                  required: true
                  final: false
                  valueDefinition: !String
                    required: true
                    final: false
                    unique: false
                    regEx: '[^&]+'
                ? 5: T5
                : !Set
                  required: true
                  final: false
                  valueDefinition: !String
                    required: true
                    final: false
                    unique: false
                    regEx: '[^&]+'
                ? 6: T6
                : !Map
                  required: true
                  final: false
                  keyDefinition: !Number
                    required: true
                    final: false
                    unique: false
                    type: UInt32
                  valueDefinition: !String
                    required: true
                    final: false
                    unique: false
                    regEx: '[^&]+'
                ? 7: T7
                : !MultiType
                  required: true
                  final: false
                  typeEnum: SimpleMarykTypeEnum
                  typeIsFinal: true
              reservedIndices: [99]
              reservedNames: [O99]
            TestMarykModel: !RootModel
              version: 1.0
              key: !Multiple
              - !Ref uint
              - !Ref bool
              - !Ref enum
              indexes:
              - !Multiple
                - !Reversed dateTime
                - !Ref enum
                - !Ref int
              - !Ref int
              - !Reversed double
              - !Ref multi.*
              - !Ref uint
              reservedIndices: [99]
              reservedNames: [reserved]
              minimumKeyScanByteRange: 0
              ? 1: [string, str, stringValue]
              : !String
                required: true
                final: false
                unique: false
                default: haha
                regEx: ha.*
              ? 2: int
              : !Number
                required: true
                final: false
                unique: false
                type: SInt32
                maxValue: 6
              ? 3: uint
              : !Number
                required: true
                final: true
                unique: false
                type: UInt32
              ? 4: double
              : !Number
                required: true
                final: false
                unique: false
                type: Float64
              ? 5: dateTime
              : !DateTime
                required: true
                final: false
                unique: false
                precision: SECONDS
              ? 6: bool
              : !Boolean
                required: true
                final: true
              ? 7: enum
              : !Enum
                required: true
                final: true
                unique: false
                enum: Option
                default: V1(1)
              ? 8: list
              : !List
                required: false
                final: false
                valueDefinition: !Number
                  required: true
                  final: false
                  unique: false
                  type: SInt32
              ? 9: set
              : !Set
                required: false
                final: false
                maxSize: 5
                valueDefinition: !Date
                  required: true
                  final: false
                  unique: false
                  maxValue: 2100-12-31
              ? 10: map
              : !Map
                required: false
                final: false
                maxSize: 5
                keyDefinition: !Time
                  required: true
                  final: false
                  unique: false
                  precision: SECONDS
                  maxValue: '23:00'
                valueDefinition: !String
                  required: true
                  final: false
                  unique: false
                  maxSize: 10
              ? 11: valueObject
              : !Value
                required: false
                final: false
                unique: false
                dataModel: TestValueObject
              ? 12: embeddedValues
              : !Embed
                required: false
                final: false
                dataModel: EmbeddedMarykModel
              ? 13: multi
              : !MultiType
                required: false
                final: false
                typeEnum: SimpleMarykTypeEnum
                typeIsFinal: true
              ? 14: reference
              : !Reference
                required: false
                final: false
                unique: false
                dataModel: TestMarykModel(7)
              ? 15: listOfString
              : !List
                required: false
                final: false
                minSize: 1
                maxSize: 6
                valueDefinition: !String
                  required: true
                  final: false
                  unique: false
                  maxSize: 10
              ? 16: selfReference
              : !Reference
                required: false
                final: false
                unique: false
                dataModel: TestMarykModel(7)
              ? 17: setOfString
              : !Set
                required: false
                final: false
                maxSize: 6
                valueDefinition: !String
                  required: true
                  final: false
                  unique: false
                  maxSize: 10
              ? 18: incMap
              : !IncMap
                required: false
                final: false
                keyNumberDescriptor: UInt32
                valueDefinition: !String
                  required: true
                  final: false
                  unique: false

            """.trimIndent()
        ) {
            checkYamlConversion(
                this.definitions,
                Definitions,
                { DefinitionsConversionContext() },
                ::compareDefinitions,
                true
            )
        }
    }
}

internal fun compareDefinitions(converted: Definitions, original: Definitions) {
    assertEquals(original.definitions.size, converted.definitions.size)

    for ((index, item) in original.definitions.withIndex()) {
        if (item is IsStorableDataModel<*>) {
            (converted.definitions[index] as? IsStorableDataModel<*>)?.let {
                compareDataModels(it, item)
            } ?: throw AssertionError("Converted Model ${converted.definitions[index]} should be an IsStorableDataModel")
        } else if (item is IndexedEnumDefinition<*>) {
            (converted.definitions[index] as? IndexedEnumDefinition<*>)?.let {
                compareEnumDefinitions(it, item)
            } ?: throw AssertionError("Converted item ${converted.definitions[index]} should be an EnumDefinition")
        }
    }
}

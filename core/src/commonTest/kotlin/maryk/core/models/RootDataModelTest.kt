package maryk.core.models

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.SInt32
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.core.yaml.MarykYamlReaders
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.ByteCollector
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option
import maryk.test.models.Option.V3
import maryk.test.models.SimpleMarykTypeEnum
import maryk.test.models.TestMarykModel
import maryk.test.models.TestValueObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

internal class RootDataModelTest {
    @Test
    fun testKey() {
        expect(
            Key(
                byteArrayOf(0, 0, 2, 43, 1, 0, 3)
            )
        ) {
            TestMarykModel.key(
                TestMarykModel(
                    string = "name",
                    int = 5123123,
                    uint = 555u,
                    double = 6.33,
                    bool = true,
                    enum = V3,
                    dateTime = DateTime.nowUTC()
                )
            )
        }
    }

    private val subModelRef = TestMarykModel { embeddedValues { value::ref } }
    private val mapRef = TestMarykModel { map::ref }
    private val mapKeyRef = TestMarykModel { map refToKey Time(12, 33, 44) }

    @Test
    fun testPropertyReferenceByName() {
        expect(mapRef) { TestMarykModel.getPropertyReferenceByName(mapRef.completeName) }
        expect(subModelRef) { TestMarykModel.getPropertyReferenceByName(subModelRef.completeName) }
    }

    @Test
    fun testPropertyReferenceByWriter() {
        val bc = ByteCollector()
        val cache = WriteCache()

        arrayOf(subModelRef, mapRef, mapKeyRef).forEach { reference ->
            bc.reserve(
                reference.calculateTransportByteLength(cache)
            )
            reference.writeTransportBytes(cache, bc::write)

            expect(reference) { TestMarykModel.getPropertyReferenceByBytes(bc.size, bc::read) }

            bc.reset()
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            TestMarykModel,
            RootDataModel.Model,
            { DefinitionsConversionContext() },
            ::compareDataModels
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        expect(
            """
            {
              "name": "TestMarykModel",
              "key": ["Multiple", [["Ref", "uint"], ["Ref", "bool"], ["Ref", "enum"]]],
              "indices": [["Multiple", [["Reversed", "dateTime"], ["Ref", "enum"], ["Ref", "int"]]], ["Ref", "int"], ["Reversed", "double"], ["Ref", "multi.*"]],
              "reservedIndices": [99],
              "reservedNames": ["reserved"],
              "properties": [{
                "index": 1,
                "name": "string",
                "alternativeNames": ["str", "stringValue"],
                "definition": ["String", {
                  "required": true,
                  "final": false,
                  "unique": false,
                  "default": "haha",
                  "regEx": "ha.*"
                }]
              }, {
                "index": 2,
                "name": "int",
                "definition": ["Number", {
                  "required": true,
                  "final": false,
                  "unique": false,
                  "type": "SInt32",
                  "maxValue": 6
                }]
              }, {
                "index": 3,
                "name": "uint",
                "definition": ["Number", {
                  "required": true,
                  "final": true,
                  "unique": false,
                  "type": "UInt32"
                }]
              }, {
                "index": 4,
                "name": "double",
                "definition": ["Number", {
                  "required": true,
                  "final": false,
                  "unique": false,
                  "type": "Float64"
                }]
              }, {
                "index": 5,
                "name": "dateTime",
                "definition": ["DateTime", {
                  "required": true,
                  "final": false,
                  "unique": false,
                  "precision": "SECONDS"
                }]
              }, {
                "index": 6,
                "name": "bool",
                "definition": ["Boolean", {
                  "required": true,
                  "final": true
                }]
              }, {
                "index": 7,
                "name": "enum",
                "definition": ["Enum", {
                  "required": true,
                  "final": true,
                  "unique": false,
                  "enum": {
                    "name": "Option",
                    "cases": {
                      "1": "V1",
                      "2": ["V2", "VERSION2"],
                      "3": ["V3", "VERSION3"]
                    },
                    "reservedIndices": [4],
                    "reservedNames": ["V4"]
                  },
                  "default": "V1(1)"
                }]
              }, {
                "index": 8,
                "name": "list",
                "definition": ["List", {
                  "required": false,
                  "final": false,
                  "valueDefinition": ["Number", {
                    "required": true,
                    "final": false,
                    "unique": false,
                    "type": "SInt32"
                  }]
                }]
              }, {
                "index": 9,
                "name": "set",
                "definition": ["Set", {
                  "required": false,
                  "final": false,
                  "maxSize": 5,
                  "valueDefinition": ["Date", {
                    "required": true,
                    "final": false,
                    "unique": false,
                    "maxValue": "2100-12-31"
                  }]
                }]
              }, {
                "index": 10,
                "name": "map",
                "definition": ["Map", {
                  "required": false,
                  "final": false,
                  "maxSize": 5,
                  "keyDefinition": ["Time", {
                    "required": true,
                    "final": false,
                    "unique": false,
                    "precision": "SECONDS",
                    "maxValue": "23:00"
                  }],
                  "valueDefinition": ["String", {
                    "required": true,
                    "final": false,
                    "unique": false,
                    "maxSize": 10
                  }]
                }]
              }, {
                "index": 11,
                "name": "valueObject",
                "definition": ["Value", {
                  "required": false,
                  "final": false,
                  "unique": false,
                  "dataModel": "TestValueObject"
                }]
              }, {
                "index": 12,
                "name": "embeddedValues",
                "definition": ["Embed", {
                  "required": false,
                  "final": false,
                  "dataModel": "EmbeddedMarykModel"
                }]
              }, {
                "index": 13,
                "name": "multi",
                "definition": ["MultiType", {
                  "required": false,
                  "final": false,
                  "typeEnum": {
                    "name": "SimpleMarykTypeEnum",
                    "cases": [{
                      "index": 1,
                      "name": "S1",
                      "alternativeNames": ["Type1"],
                      "definition": ["String", {
                        "required": true,
                        "final": false,
                        "unique": false,
                        "regEx": "[^&]+"
                      }]
                    }, {
                      "index": 2,
                      "name": "S2",
                      "definition": ["Number", {
                        "required": true,
                        "final": false,
                        "unique": false,
                        "type": "SInt16"
                      }]
                    }, {
                      "index": 3,
                      "name": "S3",
                      "definition": ["Embed", {
                        "required": true,
                        "final": false,
                        "dataModel": "EmbeddedMarykModel"
                      }]
                    }],
                    "reservedIndices": [99],
                    "reservedNames": ["O99"]
                  },
                  "typeIsFinal": true
                }]
              }, {
                "index": 14,
                "name": "reference",
                "definition": ["Reference", {
                  "required": false,
                  "final": false,
                  "unique": false,
                  "dataModel": "TestMarykModel"
                }]
              }, {
                "index": 15,
                "name": "listOfString",
                "definition": ["List", {
                  "required": false,
                  "final": false,
                  "minSize": 1,
                  "maxSize": 6,
                  "valueDefinition": ["String", {
                    "required": true,
                    "final": false,
                    "unique": false,
                    "maxSize": 10
                  }]
                }]
              }, {
                "index": 16,
                "name": "selfReference",
                "definition": ["Reference", {
                  "required": false,
                  "final": false,
                  "unique": false,
                  "dataModel": "TestMarykModel"
                }]
              }, {
                "index": 17,
                "name": "setOfString",
                "definition": ["Set", {
                  "required": false,
                  "final": false,
                  "maxSize": 6,
                  "valueDefinition": ["String", {
                    "required": true,
                    "final": false,
                    "unique": false,
                    "maxSize": 10
                  }]
                }]
              }, {
                "index": 18,
                "name": "incMap",
                "definition": ["IncMap", {
                  "required": false,
                  "final": false,
                  "keyNumberDescriptor": "UInt32",
                  "valueDefinition": ["String", {
                    "required": true,
                    "final": false,
                    "unique": false
                  }]
                }]
              }]
            }""".trimIndent()
        ) {
            checkJsonConversion(
                TestMarykModel,
                RootDataModel.Model,
                { DefinitionsConversionContext() },
                ::compareDataModels
            )
        }
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        expect(
            """
            name: TestMarykModel
            key: !Multiple
            - !Ref uint
            - !Ref bool
            - !Ref enum
            indices:
            - !Multiple
              - !Reversed dateTime
              - !Ref enum
              - !Ref int
            - !Ref int
            - !Reversed double
            - !Ref multi.*
            reservedIndices: [99]
            reservedNames: [reserved]
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
              enum:
                name: Option
                cases:
                  1: V1
                  2: [V2, VERSION2]
                  3: [V3, VERSION3]
                reservedIndices: [4]
                reservedNames: [V4]
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
              typeEnum:
                name: SimpleMarykTypeEnum
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
              typeIsFinal: true
            ? 14: reference
            : !Reference
              required: false
              final: false
              unique: false
              dataModel: TestMarykModel
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
              dataModel: TestMarykModel
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
                TestMarykModel,
                RootDataModel.Model,
                { DefinitionsConversionContext() },
                ::compareDataModels
            )
        }
    }

    @Test
    fun convertBasicDefinitionFromYAML() {
        val simpleYaml = """
        name: SimpleModel
        ? 1: [string, str]
        : !String
        ? 2: int
        : !Number
          type: SInt32
        ? 3: date
        : !Date
        ? 4: time
        : !Time
        ? 5: dateTime
        : !DateTime
        ? 6: options
        : !Enum
          enum:
            name: Option
            cases:
              1: V1
              2: V2
              3: V3
        ? 7: fixed
        : !FixedBytes
          byteSize: 4
        ? 8: flex
        : !FlexBytes
        ? 9: list
        : !List
          valueDefinition: !String
        ? 10: set
        : !Set
          valueDefinition: !Boolean
        ? 11: map
        : !Map
          keyDefinition: !Date
          valueDefinition: !String
        ? 12: embedded
        : !Embed
          dataModel: TestMarykModel
        ? 13: value
        : !Value
          dataModel: TestValueObject
        ? 14: ref
        : !Reference
          dataModel: TestMarykModel
        ? 15: multi
        : !MultiType
          typeEnum:
            name: SimpleMarykTypeEnum
            cases:
              ? 1: S1
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
        ? 16: isTrue
        : !Boolean

        """.trimIndent()

        var index = 0

        val reader = MarykYamlReaders {
            simpleYaml[index++].also {
                if (it == '\u0000') {
                    throw Throwable("0 char encountered")
                }
            }
        }

        val newContext = DefinitionsConversionContext()
        newContext.dataModels["TestMarykModel"] = { TestMarykModel }
        newContext.dataModels["TestValueObject"] = { TestValueObject }
        newContext.dataModels["EmbeddedMarykModel"] = { EmbeddedMarykModel }

        RootDataModel.Model.readJson(reader, newContext).toDataObject().apply {
            assertEquals("SimpleModel", name)

            properties["string"]!!.let {
                expect(1u) { it.index }
                expect(StringDefinition()) {
                    it.definition as StringDefinition
                }
                expect(setOf("str")) { it.alternativeNames }
            }
            expect(properties["string"]) { properties["str"] }

            properties["int"]!!.let {
                expect(2u) { it.index }
                expect(NumberDefinition(type = SInt32)) {
                    it.definition as NumberDefinition<*>
                }
            }
            properties["date"]!!.let {
                expect(3u) { it.index }
                expect(DateDefinition()) { it.definition as DateDefinition }
            }
            properties["time"]!!.let {
                expect(4u) { it.index }
                expect(TimeDefinition()) { it.definition as TimeDefinition }
            }
            properties["dateTime"]!!.let {
                expect(5u) { it.index }
                expect(DateTimeDefinition()) {
                    it.definition as DateTimeDefinition
                }
            }
            properties["options"]!!.let {
                expect(6u) { it.index }
                expect(EnumDefinition(enum = Option)) {
                    it.definition as EnumDefinition<*>
                }
            }
            properties["fixed"]!!.let {
                expect(7u) { it.index }
                expect(FixedBytesDefinition(byteSize = 4)) {
                    it.definition as FixedBytesDefinition
                }
            }
            properties["flex"]!!.let {
                expect(8u) { it.index }
                expect(FlexBytesDefinition()) {
                    it.definition as FlexBytesDefinition
                }
            }
            properties["list"]!!.let {
                expect(9u) { it.index }
                expect(ListDefinition(valueDefinition = StringDefinition())) {
                    it.definition as ListDefinition<*, *>
                }
            }
            properties["set"]!!.let {
                expect(10u) { it.index }
                expect(SetDefinition(valueDefinition = BooleanDefinition())) {
                    it.definition as SetDefinition<*, *>
                }
            }
            properties["map"]!!.let {
                expect(11u) { it.index }
                expect(MapDefinition(keyDefinition = DateDefinition(), valueDefinition = StringDefinition())) {
                    it.definition as MapDefinition<*, *, *>
                }
            }
            properties["embedded"]!!.let {
                expect(12u) { it.index }
                expect(EmbeddedValuesDefinition(dataModel = { TestMarykModel })) {
                    it.definition as EmbeddedValuesDefinition<*, *>
                }
            }
            properties["value"]!!.let {
                expect(13u) { it.index }
                expect(ValueObjectDefinition(dataModel = TestValueObject)) {
                    it.definition as ValueObjectDefinition<*, *, *>
                }
            }
            properties["ref"]!!.let {
                expect(14u) { it.index }
                expect(ReferenceDefinition(dataModel = { TestMarykModel })) {
                    it.definition as ReferenceDefinition<*>
                }
            }
            properties["multi"]!!.let {
                expect(15u) { it.index }
                expect(MultiTypeDefinition(typeEnum = SimpleMarykTypeEnum)) {
                    it.definition as MultiTypeDefinition<*, *>
                }
            }
            properties["isTrue"]!!.let {
                expect(16u) { it.index }
                expect(BooleanDefinition()) {
                    it.definition as BooleanDefinition
                }
            }
        }
    }
}

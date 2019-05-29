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
import maryk.core.properties.definitions.ValueModelDefinition
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
import maryk.test.shouldBe
import kotlin.test.Test

internal class RootDataModelTest {
    @Test
    fun testKey() {
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
        ) shouldBe Key<TestMarykModel>(
            byteArrayOf(0, 0, 2, 43, 1, 0, 3)
        )
    }

    private val subModelRef = TestMarykModel { embeddedValues { value::ref } }
    private val mapRef = TestMarykModel { map::ref }
    private val mapKeyRef = TestMarykModel { map refToKey Time(12, 33, 44) }

    @Test
    fun testPropertyReferenceByName() {
        TestMarykModel.getPropertyReferenceByName(mapRef.completeName) shouldBe mapRef
        TestMarykModel.getPropertyReferenceByName(subModelRef.completeName) shouldBe subModelRef
    }

    @Test
    fun testPropertyReferenceByWriter() {
        val bc = ByteCollector()
        val cache = WriteCache()

        arrayOf(subModelRef, mapRef, mapKeyRef).forEach {
            bc.reserve(
                it.calculateTransportByteLength(cache)
            )
            it.writeTransportBytes(cache, bc::write)

            TestMarykModel.getPropertyReferenceByBytes(bc.size, bc::read) shouldBe it

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
        checkJsonConversion(
            TestMarykModel,
            RootDataModel.Model,
            { DefinitionsConversionContext() },
            ::compareDataModels
        ) shouldBe """
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
              "maxValue": 6,
              "random": false
            }]
          }, {
            "index": 3,
            "name": "uint",
            "definition": ["Number", {
              "required": true,
              "final": true,
              "unique": false,
              "type": "UInt32",
              "random": false
            }]
          }, {
            "index": 4,
            "name": "double",
            "definition": ["Number", {
              "required": true,
              "final": false,
              "unique": false,
              "type": "Float64",
              "random": false
            }]
          }, {
            "index": 5,
            "name": "dateTime",
            "definition": ["DateTime", {
              "required": true,
              "final": false,
              "unique": false,
              "precision": "SECONDS",
              "fillWithNow": false
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
                "type": "SInt32",
                "random": false
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
                "maxValue": "2100-12-31",
                "fillWithNow": false
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
                "maxValue": "23:00",
                "fillWithNow": false
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
                    "type": "SInt16",
                    "random": false
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
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(
            TestMarykModel,
            RootDataModel.Model,
            { DefinitionsConversionContext() },
            ::compareDataModels
        ) shouldBe """
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
          random: false
        ? 3: uint
        : !Number
          required: true
          final: true
          unique: false
          type: UInt32
          random: false
        ? 4: double
        : !Number
          required: true
          final: false
          unique: false
          type: Float64
          random: false
        ? 5: dateTime
        : !DateTime
          required: true
          final: false
          unique: false
          precision: SECONDS
          fillWithNow: false
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
            random: false
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
            fillWithNow: false
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
            fillWithNow: false
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
                random: false
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
                random: false
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
            name shouldBe "SimpleModel"

            properties["string"]!!.let {
                it.index shouldBe 1u
                it.definition shouldBe StringDefinition()
                it.alternativeNames shouldBe setOf("str")
            }
            properties["str"] shouldBe properties["string"]

            properties["int"]!!.let {
                it.index shouldBe 2u
                it.definition shouldBe NumberDefinition(type = SInt32)
            }
            properties["date"]!!.let {
                it.index shouldBe 3u
                it.definition shouldBe DateDefinition()
            }
            properties["time"]!!.let {
                it.index shouldBe 4u
                it.definition shouldBe TimeDefinition()
            }
            properties["dateTime"]!!.let {
                it.index shouldBe 5u
                it.definition shouldBe DateTimeDefinition()
            }
            properties["options"]!!.let {
                it.index shouldBe 6u
                it.definition shouldBe EnumDefinition(enum = Option)
            }
            properties["fixed"]!!.let {
                it.index shouldBe 7u
                it.definition shouldBe FixedBytesDefinition(byteSize = 4)
            }
            properties["flex"]!!.let {
                it.index shouldBe 8u
                it.definition shouldBe FlexBytesDefinition()
            }
            properties["list"]!!.let {
                it.index shouldBe 9u
                it.definition shouldBe ListDefinition(
                    valueDefinition = StringDefinition()
                )
            }
            properties["set"]!!.let {
                it.index shouldBe 10u
                it.definition shouldBe SetDefinition(
                    valueDefinition = BooleanDefinition()
                )
            }
            properties["map"]!!.let {
                it.index shouldBe 11u
                it.definition shouldBe MapDefinition(
                    keyDefinition = DateDefinition(),
                    valueDefinition = StringDefinition()
                )
            }
            properties["embedded"]!!.let {
                it.index shouldBe 12u
                it.definition shouldBe EmbeddedValuesDefinition(
                    dataModel = { TestMarykModel }
                )
            }
            properties["value"]!!.let {
                it.index shouldBe 13u
                it.definition shouldBe ValueModelDefinition(
                    dataModel = TestValueObject
                )
            }
            properties["ref"]!!.let {
                it.index shouldBe 14u
                it.definition shouldBe ReferenceDefinition(
                    dataModel = { TestMarykModel }
                )
            }
            properties["multi"]!!.let {
                it.index shouldBe 15u
                it.definition shouldBe MultiTypeDefinition(
                    typeEnum = SimpleMarykTypeEnum
                )
            }
            properties["isTrue"]!!.let {
                it.index shouldBe 16u
                it.definition shouldBe BooleanDefinition()
            }
        }
    }
}

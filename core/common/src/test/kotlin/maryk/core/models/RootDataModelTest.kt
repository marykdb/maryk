package maryk.core.models

import maryk.EmbeddedMarykObject
import maryk.Option
import maryk.TestMarykModel
import maryk.TestValueObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.IsPropertyContext
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
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.WriteCache
import maryk.core.query.DataModelContext
import maryk.core.yaml.MarykYamlReader
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.shouldBe
import kotlin.test.Test

internal class RootDataModelTest {
    @Test
    fun testKey() {
        TestMarykModel.key(
            TestMarykModel(
                string = "name",
                int = 5123123,
                uint = 555.toUInt32(),
                double = 6.33,
                bool = true,
                enum = Option.V2,
                dateTime = DateTime.nowUTC()
            )
        ) shouldBe Bytes(
            byteArrayOf(0, 0, 2, 43, 1, 1, 1, 0, 2)
        )
    }

    private val subModelRef = EmbeddedMarykObject.Properties.value.getRef(
        TestMarykModel.Properties.embeddedValues.getRef()
    )
    private val mapRef = TestMarykModel.ref { map }
    private val mapKeyRef = TestMarykModel { map key Time(12, 33, 44) }

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
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(TestMarykModel, RootDataModel.Model, { DataModelContext() }, ::compareDataModels)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(
            TestMarykModel,
            RootDataModel.Model,
            { DataModelContext() },
            ::compareDataModels
        ) shouldBe """
        {
        	"name": "TestMarykModel",
        	"key": [["Ref", "uint"], ["Ref", "bool"], ["Ref", "enum"]],
        	"properties": [{
        		"index": 0,
        		"name": "string",
        		"definition": ["String", {
        			"indexed": false,
        			"required": true,
        			"final": false,
        			"unique": false,
        			"default": "haha",
        			"regEx": "ha.*"
        		}]
        	}, {
        		"index": 1,
        		"name": "int",
        		"definition": ["Number", {
        			"indexed": false,
        			"required": true,
        			"final": false,
        			"unique": false,
        			"type": "SInt32",
        			"maxValue": 6,
        			"random": false
        		}]
        	}, {
        		"index": 2,
        		"name": "uint",
        		"definition": ["Number", {
        			"indexed": false,
        			"required": true,
        			"final": true,
        			"unique": false,
        			"type": "UInt32",
        			"random": false
        		}]
        	}, {
        		"index": 3,
        		"name": "double",
        		"definition": ["Number", {
        			"indexed": false,
        			"required": true,
        			"final": false,
        			"unique": false,
        			"type": "Float64",
        			"random": false
        		}]
        	}, {
        		"index": 4,
        		"name": "dateTime",
        		"definition": ["DateTime", {
        			"indexed": false,
        			"required": true,
        			"final": false,
        			"unique": false,
        			"precision": "SECONDS",
        			"fillWithNow": false
        		}]
        	}, {
        		"index": 5,
        		"name": "bool",
        		"definition": ["Boolean", {
        			"indexed": false,
        			"required": true,
        			"final": true
        		}]
        	}, {
        		"index": 6,
        		"name": "enum",
        		"definition": ["Enum", {
        			"indexed": false,
        			"required": true,
        			"final": true,
        			"unique": false,
        			"enum": {
        				"name": "Option",
        				"values": {
        					"0": "V0",
        					"1": "V1",
        					"2": "V2"
        				}
        			},
        			"default": "V0"
        		}]
        	}, {
        		"index": 7,
        		"name": "list",
        		"definition": ["List", {
        			"indexed": false,
        			"required": false,
        			"final": false,
        			"valueDefinition": ["Number", {
        				"indexed": false,
        				"required": true,
        				"final": false,
        				"unique": false,
        				"type": "SInt32",
        				"random": false
        			}]
        		}]
        	}, {
        		"index": 8,
        		"name": "set",
        		"definition": ["Set", {
        			"indexed": false,
        			"required": false,
        			"final": false,
        			"valueDefinition": ["Date", {
        				"indexed": false,
        				"required": true,
        				"final": false,
        				"unique": false,
        				"fillWithNow": false
        			}]
        		}]
        	}, {
        		"index": 9,
        		"name": "map",
        		"definition": ["Map", {
        			"indexed": false,
        			"required": false,
        			"final": false,
        			"keyDefinition": ["Time", {
        				"indexed": false,
        				"required": true,
        				"final": false,
        				"unique": false,
        				"precision": "SECONDS",
        				"fillWithNow": false
        			}],
        			"valueDefinition": ["String", {
        				"indexed": false,
        				"required": true,
        				"final": false,
        				"unique": false
        			}]
        		}]
        	}, {
        		"index": 10,
        		"name": "valueObject",
        		"definition": ["Value", {
        			"indexed": false,
        			"required": false,
        			"final": false,
        			"unique": false,
        			"dataModel": "TestValueObject"
        		}]
        	}, {
        		"index": 11,
        		"name": "embeddedValues",
        		"definition": ["Embed", {
        			"indexed": false,
        			"required": false,
        			"final": false,
        			"dataModel": "EmbeddedMarykModel"
        		}]
        	}, {
        		"index": 12,
        		"name": "multi",
        		"definition": ["MultiType", {
        			"indexed": false,
        			"required": false,
        			"final": false,
        			"typeEnum": "Option",
        			"definitionMap": [{
        				"index": 0,
        				"name": "V0",
        				"definition": ["String", {
        					"indexed": false,
        					"required": true,
        					"final": false,
        					"unique": false
        				}]
        			}, {
        				"index": 1,
        				"name": "V1",
        				"definition": ["Number", {
        					"indexed": false,
        					"required": true,
        					"final": false,
        					"unique": false,
        					"type": "SInt32",
        					"random": false
        				}]
        			}, {
        				"index": 2,
        				"name": "V2",
        				"definition": ["Embed", {
        					"indexed": false,
        					"required": true,
        					"final": false,
        					"dataModel": "EmbeddedMarykModel"
        				}]
        			}]
        		}]
        	}, {
        		"index": 13,
        		"name": "reference",
        		"definition": ["Reference", {
        			"indexed": false,
        			"required": false,
        			"final": false,
        			"unique": false,
        			"dataModel": "TestMarykModel"
        		}]
        	}, {
        		"index": 14,
        		"name": "listOfString",
        		"definition": ["List", {
        			"indexed": false,
        			"required": false,
        			"final": false,
        			"valueDefinition": ["String", {
        				"indexed": false,
        				"required": true,
        				"final": false,
        				"unique": false
        			}]
        		}]
        	}, {
        		"index": 15,
        		"name": "selfReference",
        		"definition": ["Reference", {
        			"indexed": false,
        			"required": false,
        			"final": false,
        			"unique": false,
        			"dataModel": "TestMarykModel"
        		}]
        	}]
        }""".trimIndent()
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(
            TestMarykModel,
            RootDataModel.Model,
            { DataModelContext() },
            ::compareDataModels
        ) shouldBe """
        name: TestMarykModel
        key:
        - !Ref uint
        - !Ref bool
        - !Ref enum
        properties:
          ? 0: string
          : !String
            indexed: false
            required: true
            final: false
            unique: false
            default: haha
            regEx: ha.*
          ? 1: int
          : !Number
            indexed: false
            required: true
            final: false
            unique: false
            type: SInt32
            maxValue: 6
            random: false
          ? 2: uint
          : !Number
            indexed: false
            required: true
            final: true
            unique: false
            type: UInt32
            random: false
          ? 3: double
          : !Number
            indexed: false
            required: true
            final: false
            unique: false
            type: Float64
            random: false
          ? 4: dateTime
          : !DateTime
            indexed: false
            required: true
            final: false
            unique: false
            precision: SECONDS
            fillWithNow: false
          ? 5: bool
          : !Boolean
            indexed: false
            required: true
            final: true
          ? 6: enum
          : !Enum
            indexed: false
            required: true
            final: true
            unique: false
            enum:
              name: Option
              values:
                0: V0
                1: V1
                2: V2
            default: V0
          ? 7: list
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
          ? 8: set
          : !Set
            indexed: false
            required: false
            final: false
            valueDefinition: !Date
              indexed: false
              required: true
              final: false
              unique: false
              fillWithNow: false
          ? 9: map
          : !Map
            indexed: false
            required: false
            final: false
            keyDefinition: !Time
              indexed: false
              required: true
              final: false
              unique: false
              precision: SECONDS
              fillWithNow: false
            valueDefinition: !String
              indexed: false
              required: true
              final: false
              unique: false
          ? 10: valueObject
          : !Value
            indexed: false
            required: false
            final: false
            unique: false
            dataModel: TestValueObject
          ? 11: embeddedValues
          : !Embed
            indexed: false
            required: false
            final: false
            dataModel: EmbeddedMarykModel
          ? 12: multi
          : !MultiType
            indexed: false
            required: false
            final: false
            typeEnum: Option
            definitionMap:
              ? 0: V0
              : !String
                indexed: false
                required: true
                final: false
                unique: false
              ? 1: V1
              : !Number
                indexed: false
                required: true
                final: false
                unique: false
                type: SInt32
                random: false
              ? 2: V2
              : !Embed
                indexed: false
                required: true
                final: false
                dataModel: EmbeddedMarykModel
          ? 13: reference
          : !Reference
            indexed: false
            required: false
            final: false
            unique: false
            dataModel: TestMarykModel
          ? 14: listOfString
          : !List
            indexed: false
            required: false
            final: false
            valueDefinition: !String
              indexed: false
              required: true
              final: false
              unique: false
          ? 15: selfReference
          : !Reference
            indexed: false
            required: false
            final: false
            unique: false
            dataModel: TestMarykModel

        """.trimIndent()
    }

    @Test
    fun convert_basic_definition_from_YAML() {
        val simpleYaml = """
        name: SimpleModel
        properties:
          ? 0: string
          : !String
          ? 1: int
          : !Number
            type: SInt32
          ? 2: date
          : !Date
          ? 3: time
          : !Time
          ? 4: dateTime
          : !DateTime
          ? 5: options
          : !Enum
            enum:
              name: Option
              values:
                0: V0
                1: V1
                2: V2
          ? 6: fixed
          : !FixedBytes
            byteSize: 4
          ? 7: flex
          : !FlexBytes
          ? 8: list
          : !List
            valueDefinition: !String
          ? 9: set
          : !Set
            valueDefinition: !Boolean
          ? 10: map
          : !Map
            keyDefinition: !Date
            valueDefinition: !String
          ? 11: embedded
          : !Embed
            dataModel: TestMarykModel
          ? 12: value
          : !Value
            dataModel: TestValueObject
          ? 13: ref
          : !Reference
            dataModel: TestMarykModel
          ? 14: multi
          : !MultiType
            typeEnum: Option
            definitionMap:
              ? 0: V0
              : !String
              ? 1: V1
              : !Boolean
          ? 15: isTrue
          : !Boolean

        """.trimIndent()

        var index = 0

        val reader = MarykYamlReader {
            simpleYaml[index++].also {
                if (it == '\u0000') {
                    throw Throwable("0 char encountered")
                }
            }
        }

        val newContext = DataModelContext()
        newContext.dataModels["TestMarykModel"] = { TestMarykModel }
        newContext.dataModels["TestValueObject"] = { TestValueObject }

        RootDataModel.Model.readJson(reader, newContext).toDataObject().apply {
            name shouldBe "SimpleModel"

            properties.get("string")!!.let {
                it.index shouldBe 0
                it.definition shouldBe StringDefinition()
            }
            properties.get("int")!!.let {
                it.index shouldBe 1
                it.definition shouldBe NumberDefinition(type = SInt32)
            }
            properties.get("date")!!.let {
                it.index shouldBe 2
                it.definition shouldBe DateDefinition()
            }
            properties.get("time")!!.let {
                it.index shouldBe 3
                it.definition shouldBe TimeDefinition()
            }
            properties.get("dateTime")!!.let {
                it.index shouldBe 4
                it.definition shouldBe DateTimeDefinition()
            }
            properties.get("options")!!.let {
                it.index shouldBe 5
                it.definition shouldBe EnumDefinition(enum = Option)
            }
            properties.get("fixed")!!.let {
                it.index shouldBe 6
                it.definition shouldBe FixedBytesDefinition(byteSize = 4)
            }
            properties.get("flex")!!.let {
                it.index shouldBe 7
                it.definition shouldBe FlexBytesDefinition()
            }
            properties.get("list")!!.let {
                it.index shouldBe 8
                it.definition shouldBe ListDefinition(
                    valueDefinition = StringDefinition()
                )
            }
            properties.get("set")!!.let {
                it.index shouldBe 9
                it.definition shouldBe SetDefinition(
                    valueDefinition = BooleanDefinition()
                )
            }
            properties.get("map")!!.let {
                it.index shouldBe 10
                it.definition shouldBe MapDefinition(
                    keyDefinition = DateDefinition(),
                    valueDefinition = StringDefinition()
                )
            }
            properties.get("embedded")!!.let {
                it.index shouldBe 11
                it.definition shouldBe EmbeddedValuesDefinition(
                    dataModel = { TestMarykModel }
                )
            }
            properties.get("value")!!.let {
                it.index shouldBe 12
                it.definition shouldBe ValueModelDefinition(
                    dataModel = TestValueObject
                )
            }
            properties.get("ref")!!.let {
                it.index shouldBe 13
                it.definition shouldBe ReferenceDefinition(
                    dataModel = { TestMarykModel }
                )
            }
            properties.get("multi")!!.let {
                it.index shouldBe 14
                it.definition shouldBe MultiTypeDefinition<Option, IsPropertyContext>(
                    typeEnum = Option,
                    definitionMap = mapOf(
                        Option.V0 to StringDefinition(),
                        Option.V1 to BooleanDefinition()
                    )
                )
            }
            properties.get("isTrue")!!.let {
                it.index shouldBe 15
                it.definition shouldBe BooleanDefinition()
            }
        }
    }
}

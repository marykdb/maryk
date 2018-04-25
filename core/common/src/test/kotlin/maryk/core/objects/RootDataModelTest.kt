package maryk.core.objects

import maryk.Option
import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.TestValueObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueModelDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.comparePropertyDefinitionWrapper
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
        TestMarykObject.key(
            TestMarykObject(
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

    private val subModelRef = SubMarykObject.Properties.value.getRef(TestMarykObject.Properties.subModel.getRef())
    private val mapRef = TestMarykObject.ref { map }
    private val mapKeyRef = TestMarykObject { map key Time(12, 33, 44) }

    @Test
    fun testPropertyReferenceByName() {
        TestMarykObject.getPropertyReferenceByName(mapRef.completeName) shouldBe mapRef
        TestMarykObject.getPropertyReferenceByName(subModelRef.completeName) shouldBe subModelRef
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

            TestMarykObject.getPropertyReferenceByBytes(bc.size, bc::read) shouldBe it

            bc.reset()
        }
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(TestMarykObject, RootDataModel.Model, DataModelContext(), ::compareDataModels)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(
            TestMarykObject,
            RootDataModel.Model,
            DataModelContext(),
            ::compareDataModels
        ) shouldBe """
        |{
        |	"name": "TestMarykObject",
        |	"key": [["Ref", "uint"], ["Ref", "bool"], ["Ref", "enum"]],
        |	"properties": [{
        |		"index": 0,
        |		"name": "string",
        |		"definition": ["String", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": true,
        |			"final": false,
        |			"unique": false,
        |			"regEx": "ha.*"
        |		}]
        |	}, {
        |		"index": 1,
        |		"name": "int",
        |		"definition": ["Number", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": true,
        |			"final": false,
        |			"unique": false,
        |			"type": "SINT32",
        |			"maxValue": 6,
        |			"random": false
        |		}]
        |	}, {
        |		"index": 2,
        |		"name": "uint",
        |		"definition": ["Number", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": true,
        |			"final": true,
        |			"unique": false,
        |			"type": "UINT32",
        |			"random": false
        |		}]
        |	}, {
        |		"index": 3,
        |		"name": "double",
        |		"definition": ["Number", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": true,
        |			"final": false,
        |			"unique": false,
        |			"type": "FLOAT64",
        |			"random": false
        |		}]
        |	}, {
        |		"index": 4,
        |		"name": "dateTime",
        |		"definition": ["DateTime", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": true,
        |			"final": false,
        |			"unique": false,
        |			"fillWithNow": false,
        |			"precision": "SECONDS"
        |		}]
        |	}, {
        |		"index": 5,
        |		"name": "bool",
        |		"definition": ["Boolean", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": true,
        |			"final": true
        |		}]
        |	}, {
        |		"index": 6,
        |		"name": "enum",
        |		"definition": ["Enum", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": true,
        |			"final": true,
        |			"unique": false,
        |			"values": {
        |				"0": "V0",
        |				"1": "V1",
        |				"2": "V2"
        |			}
        |		}]
        |	}, {
        |		"index": 7,
        |		"name": "list",
        |		"definition": ["List", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": false,
        |			"final": false,
        |			"valueDefinition": ["Number", {
        |				"indexed": false,
        |				"searchable": true,
        |				"required": true,
        |				"final": false,
        |				"unique": false,
        |				"type": "SINT32",
        |				"random": false
        |			}]
        |		}]
        |	}, {
        |		"index": 8,
        |		"name": "set",
        |		"definition": ["Set", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": false,
        |			"final": false,
        |			"valueDefinition": ["Date", {
        |				"indexed": false,
        |				"searchable": true,
        |				"required": true,
        |				"final": false,
        |				"unique": false,
        |				"fillWithNow": false
        |			}]
        |		}]
        |	}, {
        |		"index": 9,
        |		"name": "map",
        |		"definition": ["Map", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": false,
        |			"final": false,
        |			"keyDefinition": ["Time", {
        |				"indexed": false,
        |				"searchable": true,
        |				"required": true,
        |				"final": false,
        |				"unique": false,
        |				"fillWithNow": false,
        |				"precision": "SECONDS"
        |			}],
        |			"valueDefinition": ["String", {
        |				"indexed": false,
        |				"searchable": true,
        |				"required": true,
        |				"final": false,
        |				"unique": false
        |			}]
        |		}]
        |	}, {
        |		"index": 10,
        |		"name": "valueObject",
        |		"definition": ["ValueModel", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": false,
        |			"final": false,
        |			"unique": false,
        |			"dataModel": "TestValueObject"
        |		}]
        |	}, {
        |		"index": 11,
        |		"name": "subModel",
        |		"definition": ["SubModel", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": false,
        |			"final": false,
        |			"dataModel": "SubMarykObject"
        |		}]
        |	}, {
        |		"index": 12,
        |		"name": "multi",
        |		"definition": ["MultiType", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": false,
        |			"final": false,
        |			"definitionMap": [{
        |				"index": 0,
        |				"name": "V0",
        |				"definition": ["String", {
        |					"indexed": false,
        |					"searchable": true,
        |					"required": true,
        |					"final": false,
        |					"unique": false
        |				}]
        |			}, {
        |				"index": 1,
        |				"name": "V1",
        |				"definition": ["Number", {
        |					"indexed": false,
        |					"searchable": true,
        |					"required": true,
        |					"final": false,
        |					"unique": false,
        |					"type": "SINT32",
        |					"random": false
        |				}]
        |			}, {
        |				"index": 2,
        |				"name": "V2",
        |				"definition": ["SubModel", {
        |					"indexed": false,
        |					"searchable": true,
        |					"required": true,
        |					"final": false,
        |					"dataModel": "SubMarykObject"
        |				}]
        |			}]
        |		}]
        |	}, {
        |		"index": 13,
        |		"name": "reference",
        |		"definition": ["Reference", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": false,
        |			"final": false,
        |			"unique": false,
        |			"dataModel": "TestMarykObject"
        |		}]
        |	}, {
        |		"index": 14,
        |		"name": "listOfString",
        |		"definition": ["List", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": false,
        |			"final": false,
        |			"valueDefinition": ["String", {
        |				"indexed": false,
        |				"searchable": true,
        |				"required": true,
        |				"final": false,
        |				"unique": false
        |			}]
        |		}]
        |	}, {
        |		"index": 15,
        |		"name": "selfReference",
        |		"definition": ["Reference", {
        |			"indexed": false,
        |			"searchable": true,
        |			"required": false,
        |			"final": false,
        |			"unique": false,
        |			"dataModel": "TestMarykObject"
        |		}]
        |	}]
        |}""".trimMargin()
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(
            TestMarykObject,
            RootDataModel.Model,
            DataModelContext(),
            ::compareDataModels
        ) shouldBe """
        |name: TestMarykObject
        |key:
        |- !Ref uint
        |- !Ref bool
        |- !Ref enum
        |properties:
        |  ? 0: string
        |  : !String
        |    indexed: false
        |    searchable: true
        |    required: true
        |    final: false
        |    unique: false
        |    regEx: ha.*
        |  ? 1: int
        |  : !Number
        |    indexed: false
        |    searchable: true
        |    required: true
        |    final: false
        |    unique: false
        |    type: SINT32
        |    maxValue: 6
        |    random: false
        |  ? 2: uint
        |  : !Number
        |    indexed: false
        |    searchable: true
        |    required: true
        |    final: true
        |    unique: false
        |    type: UINT32
        |    random: false
        |  ? 3: double
        |  : !Number
        |    indexed: false
        |    searchable: true
        |    required: true
        |    final: false
        |    unique: false
        |    type: FLOAT64
        |    random: false
        |  ? 4: dateTime
        |  : !DateTime
        |    indexed: false
        |    searchable: true
        |    required: true
        |    final: false
        |    unique: false
        |    fillWithNow: false
        |    precision: SECONDS
        |  ? 5: bool
        |  : !Boolean
        |    indexed: false
        |    searchable: true
        |    required: true
        |    final: true
        |  ? 6: enum
        |  : !Enum
        |    indexed: false
        |    searchable: true
        |    required: true
        |    final: true
        |    unique: false
        |    values:
        |      0: V0
        |      1: V1
        |      2: V2
        |  ? 7: list
        |  : !List
        |    indexed: false
        |    searchable: true
        |    required: false
        |    final: false
        |    valueDefinition: !Number
        |      indexed: false
        |      searchable: true
        |      required: true
        |      final: false
        |      unique: false
        |      type: SINT32
        |      random: false
        |  ? 8: set
        |  : !Set
        |    indexed: false
        |    searchable: true
        |    required: false
        |    final: false
        |    valueDefinition: !Date
        |      indexed: false
        |      searchable: true
        |      required: true
        |      final: false
        |      unique: false
        |      fillWithNow: false
        |  ? 9: map
        |  : !Map
        |    indexed: false
        |    searchable: true
        |    required: false
        |    final: false
        |    keyDefinition: !Time
        |      indexed: false
        |      searchable: true
        |      required: true
        |      final: false
        |      unique: false
        |      fillWithNow: false
        |      precision: SECONDS
        |    valueDefinition: !String
        |      indexed: false
        |      searchable: true
        |      required: true
        |      final: false
        |      unique: false
        |  ? 10: valueObject
        |  : !ValueModel
        |    indexed: false
        |    searchable: true
        |    required: false
        |    final: false
        |    unique: false
        |    dataModel: TestValueObject
        |  ? 11: subModel
        |  : !SubModel
        |    indexed: false
        |    searchable: true
        |    required: false
        |    final: false
        |    dataModel: SubMarykObject
        |  ? 12: multi
        |  : !MultiType
        |    indexed: false
        |    searchable: true
        |    required: false
        |    final: false
        |    definitionMap:
        |      ? 0: V0
        |      : !String
        |        indexed: false
        |        searchable: true
        |        required: true
        |        final: false
        |        unique: false
        |      ? 1: V1
        |      : !Number
        |        indexed: false
        |        searchable: true
        |        required: true
        |        final: false
        |        unique: false
        |        type: SINT32
        |        random: false
        |      ? 2: V2
        |      : !SubModel
        |        indexed: false
        |        searchable: true
        |        required: true
        |        final: false
        |        dataModel: SubMarykObject
        |  ? 13: reference
        |  : !Reference
        |    indexed: false
        |    searchable: true
        |    required: false
        |    final: false
        |    unique: false
        |    dataModel: TestMarykObject
        |  ? 14: listOfString
        |  : !List
        |    indexed: false
        |    searchable: true
        |    required: false
        |    final: false
        |    valueDefinition: !String
        |      indexed: false
        |      searchable: true
        |      required: true
        |      final: false
        |      unique: false
        |  ? 15: selfReference
        |  : !Reference
        |    indexed: false
        |    searchable: true
        |    required: false
        |    final: false
        |    unique: false
        |    dataModel: TestMarykObject
        |""".trimMargin()
    }


    @Test
    fun convert_basic_definition_from_YAML() {
        val simpleYaml = """
        |name: SimpleModel
        |properties:
        |  ? 0: string
        |  : !String
        |  ? 1: int
        |  : !Number
        |    type: SINT32
        |  ? 2: date
        |  : !Date
        |  ? 3: time
        |  : !Time
        |  ? 4: dateTime
        |  : !DateTime
        |  ? 5: options
        |  : !Enum
        |    values:
        |      0: V0
        |      1: V1
        |      2: V2
        |  ? 6: fixed
        |  : !FixedBytes
        |    byteSize: 4
        |  ? 7: flex
        |  : !FlexBytes
        |  ? 8: list
        |  : !List
        |    valueDefinition: !String
        |  ? 9: set
        |  : !Set
        |    valueDefinition: !Boolean
        |  ? 10: map
        |  : !Map
        |    keyDefinition: !Date
        |    valueDefinition: !String
        |  ? 11: model
        |  : !SubModel
        |    dataModel: TestMarykObject
        |  ? 12: value
        |  : !ValueModel
        |    dataModel: TestValueObject
        |  ? 13: ref
        |  : !Reference
        |    dataModel: TestMarykObject
        |  ? 14: multi
        |  : !MultiType
        |    definitionMap:
        |      ? 0: V0
        |      : !String
        |      ? 1: V1
        |      : !Boolean
        |  ? 15: isTrue
        |  : !Boolean
        |""".trimMargin()

        var index = 0

        val reader = MarykYamlReader {
            simpleYaml[index++].also {
                if (it == '\u0000') {
                    throw Throwable("0 char encountered")
                }
            }
        }

        val newContext = DataModelContext()
        newContext.dataModels["TestMarykObject"] = TestMarykObject
        newContext.dataModels["TestValueObject"] = TestValueObject

        RootDataModel.Model.readJsonToObject(reader, newContext).apply {
            name shouldBe "SimpleModel"

            properties.getDefinition("string")!!.let {
                it.index shouldBe 0
                it.definition shouldBe StringDefinition()
            }
            properties.getDefinition("int")!!.let {
                it.index shouldBe 1
                it.definition shouldBe NumberDefinition(type = SInt32)
            }
            properties.getDefinition("date")!!.let {
                it.index shouldBe 2
                it.definition shouldBe DateDefinition()
            }
            properties.getDefinition("time")!!.let {
                it.index shouldBe 3
                it.definition shouldBe TimeDefinition()
            }
            properties.getDefinition("dateTime")!!.let {
                it.index shouldBe 4
                it.definition shouldBe DateTimeDefinition()
            }
            properties.getDefinition("options")!!.let {
                it.index shouldBe 5
                it.definition shouldBe EnumDefinition(values = Option.values())
            }
            properties.getDefinition("fixed")!!.let {
                it.index shouldBe 6
                it.definition shouldBe FixedBytesDefinition(byteSize = 4)
            }
            properties.getDefinition("flex")!!.let {
                it.index shouldBe 7
                it.definition shouldBe FlexBytesDefinition()
            }
            properties.getDefinition("list")!!.let {
                it.index shouldBe 8
                it.definition shouldBe ListDefinition(
                    valueDefinition = StringDefinition()
                )
            }
            properties.getDefinition("set")!!.let {
                it.index shouldBe 9
                it.definition shouldBe SetDefinition(
                    valueDefinition = BooleanDefinition()
                )
            }
            properties.getDefinition("map")!!.let {
                it.index shouldBe 10
                it.definition shouldBe MapDefinition(
                    keyDefinition = DateDefinition(),
                    valueDefinition = StringDefinition()
                )
            }
            properties.getDefinition("model")!!.let {
                it.index shouldBe 11
                it.definition shouldBe SubModelDefinition(
                    dataModel = { TestMarykObject }
                )
            }
            properties.getDefinition("value")!!.let {
                it.index shouldBe 12
                it.definition shouldBe ValueModelDefinition(
                    dataModel = TestValueObject
                )
            }
            properties.getDefinition("ref")!!.let {
                it.index shouldBe 13
                it.definition shouldBe ReferenceDefinition(
                    dataModel = { TestMarykObject }
                )
            }
            properties.getDefinition("multi")!!.let {
                it.index shouldBe 14
                it.definition shouldBe MultiTypeDefinition<Option, IsPropertyContext>(
                    definitionMap = mapOf(
                        Option.V0 to StringDefinition(),
                        Option.V1 to BooleanDefinition()
                    )
                )
            }
            properties.getDefinition("isTrue")!!.let {
                it.index shouldBe 15
                it.definition shouldBe BooleanDefinition()
            }
        }


    }

    private fun compareDataModels(converted: RootDataModel<out Any, out PropertyDefinitions<out Any>>, original: RootDataModel<out Any, out PropertyDefinitions<out Any>>) {
        converted.name shouldBe original.name

        (converted.properties)
            .zip(original.properties)
            .forEach { (convertedWrapper, originalWrapper) ->
                comparePropertyDefinitionWrapper(convertedWrapper, originalWrapper)
            }

        converted.key.keyDefinitions.zip(original.key.keyDefinitions).forEach { (converted, original) ->
            when(converted) {
                is IsPropertyDefinitionWrapper<*, *, *> -> {
                    comparePropertyDefinitionWrapper(converted, original as IsPropertyDefinitionWrapper<*, *, *>)
                }
                else -> converted shouldBe original
            }
        }
    }
}

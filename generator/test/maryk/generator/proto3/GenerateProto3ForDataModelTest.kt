package maryk.generator.proto3

import maryk.generator.kotlin.GenerationContext
import maryk.generator.DecimalGeneratorModel
import maryk.core.models.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.query.DefinitionsConversionContext
import maryk.core.yaml.MarykYamlModelReader
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.NumericMarykModel
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

val generatedProto3ForSimpleMarykModel = """
message SimpleMarykModel {
  string value = 1;
}
""".trimIndent()

val generatedProto3ForEmbeddedMarykModel = """
message EmbeddedMarykModel {
  reserved 999;
  reserved "reserved";
  string value = 1;
  EmbeddedMarykModel model = 2;
  TestMarykModel marykModel = 3;
}
""".trimIndent()

val generatedProto3ForNumericMarykModel = """
message NumericMarykModel {
  sint32 sInt8 = 1;
  sint32 sInt16 = 2;
  sint32 sInt32 = 3;
  sint64 sInt64 = 4;
  uint64 uInt8 = 5;
  uint64 uInt16 = 6;
  uint64 uInt32 = 7;
  uint64 uInt64 = 8;
  float float32 = 9;
  double float64 = 10;
}
""".trimIndent()

val generatedProto3ForTestMarykModel = """
message TestMarykModel {
  reserved 99;
  reserved "reserved";
  enum Option {
    UNKNOWN_OPTION = 0;
    V1 = 1;
    V2 = 2;
    V3 = 3;
  }
  message MultiType {
    oneof multi {
      string s1 = 1;
      sint32 s2 = 2;
      EmbeddedMarykModel s3 = 3;
    }
  }
  string string = 1;
  sint32 int = 2;
  uint64 uint = 3;
  double double = 4;
  int64 dateTime = 5;
  bool bool = 6;
  Option enum = 7;
  repeated sint32 list = 8;
  repeated sint32 set = 9;
  map<uint32, string> map = 10;
  bytes valueObject = 11;
  EmbeddedMarykModel embeddedValues = 12;
  MultiType multi = 13;
  bytes reference = 14;
  repeated string listOfString = 15;
  bytes selfReference = 16;
  repeated string setOfString = 17;
  map<uint64, string> incMap = 18;
}
""".trimIndent()

val generatedProto3ForCompleteMarykModel = """
message CompleteMarykModel {
  reserved 99;
  reserved "reserved";
  enum Option {
    UNKNOWN_OPTION = 0;
    V1 = 1;
    V2 = 2;
    V3 = 3;
  }
  message T7Type {
    oneof T7 {
      string s1 = 1;
      sint32 s2 = 2;
      EmbeddedMarykModel s3 = 3;
    }
  }
  // Only one of the properties can be set. Is not a `oneof` because of a repeated type or map
  message MultiType {
    string t1 = 1;
    sint32 t2 = 2;
    EmbeddedMarykModel t3 = 3;
    repeated string t4 = 4;
    repeated string t5 = 5;
    map<uint64, string> t6 = 6;
    T7Type t7 = 7;
  }
  message MultiForKeyType {
    oneof multiForKey {
      string s1 = 1;
      sint32 s2 = 2;
      EmbeddedMarykModel s3 = 3;
    }
  }
  enum MarykEnumEmbedded {
    UNKNOWN_MARYKENUMEMBEDDED = 0;
    E1 = 1;
    E2 = 2;
    E3 = 3;
  }
  message MapWithEnumEntry {
    MarykEnumEmbedded key = 1;
    string value = 2;
  }
  message MapWithListEntry {
    string key = 1;
    repeated string value = 2;
  }
  message MapWithSetEntry {
    string key = 1;
    repeated string value = 2;
  }
  message MapWithMapEntry {
    string key = 1;
    map<string, string> value = 2;
  }
  string string = 1;
  uint64 number = 2;
  bool boolean = 3;
  Option enum = 4;
  sint32 date = 5;
  int64 dateTime = 6;
  uint32 time = 7;
  bytes fixedBytes = 8;
  bytes flexBytes = 9;
  bytes reference = 10;
  SimpleMarykModel subModel = 11;
  bytes valueModel = 12;
  repeated string list = 13;
  repeated sint32 set = 14;
  map<sint32, sint32> map = 15;
  MultiType multi = 16;
  bool booleanForKey = 17;
  sint32 dateForKey = 18;
  MultiForKeyType multiForKey = 19;
  MarykEnumEmbedded enumEmbedded = 20;
  repeated MapWithEnumEntry mapWithEnum = 21;
  repeated MapWithListEntry mapWithList = 22;
  repeated MapWithSetEntry mapWithSet = 23;
  repeated MapWithMapEntry mapWithMap = 24;
  map<uint64, EmbeddedMarykModel> incMap = 25;
  fixed64 location = 26;
}
""".trimIndent()

class GenerateProto3ForDataModelTest {
    @Test
    fun rejectsModelNamesThatAreInvalidProtoIdentifiers() {
        val exception = assertFailsWith<IllegalArgumentException> {
            buildString {
                `invalid-model`.generateProto3Schema(GenerationContext(), ::append)
            }
        }

        assertEquals("Proto3 identifier is invalid: invalid-model", exception.message)
    }

    @Test
    fun rejectsInvalidEmbeddedModelTypeNames() {
        val exception = assertFailsWith<IllegalArgumentException> {
            buildString {
                ModelWithInvalidEmbeddedType.generateProto3Schema(GenerationContext(), ::append)
            }
        }

        assertEquals("Proto3 identifier is invalid: invalid-embedded", exception.message)
    }

    @Test
    fun rejectsMultiTypeCaseIndexesOutsideTheProtoFieldNumberRange() {
        val exception = assertFailsWith<IllegalArgumentException> {
            buildString {
                ModelWithOutOfRangeMultiType.generateProto3Schema(GenerationContext(), ::append)
            }
        }

        assertEquals("Proto3 field number is invalid: 536870912", exception.message)
    }

    @Test
    fun escapesReservedNamesAsProtoStringLiterals() {
        val output = buildString {
            ReservedNamesEscapingModel.generateProto3Schema(GenerationContext(), ::append)
        }

        assertEquals(
            """
            message ReservedNamesEscapingModel {
              reserved "quote\" and newline\n";
              string value = 1;
            }
            """.trimIndent(),
            output,
        )
    }

    @Test
    fun rejectsInvalidOrdinaryPropertyFieldNumbers() {
        val exception = assertFailsWith<IllegalArgumentException> {
            536_870_912u.requireProto3FieldNumber()
        }

        assertEquals("Proto3 field number is invalid: 536870912", exception.message)
    }

    @Test
    fun rejectsReservedFieldNumbersOutsideTheProtoRange() {
        for ((indices, expected) in listOf(
            "[0]" to "Proto3 field number is invalid: 0",
            "[19000]" to "Proto3 field number is invalid: 19000",
            "[2, 2]" to "Proto3 model InvalidReservation contains duplicate reserved field number 2",
        )) {
            val model = rootModel(
                """
                name: InvalidReservation
                reservedIndices: $indices
                ? 1: value
                : !String
                """.trimIndent(),
            )

            val exception = assertFailsWith<IllegalArgumentException> {
                model.generateProto3Schema(GenerationContext()) {}
            }

            assertEquals(expected, exception.message)
        }
    }

    @Test
    fun rejectsDuplicateReservedFieldNames() {
        val model = rootModel(
            """
            name: DuplicateReservedName
            reservedNames: [removed, removed]
            ? 1: value
            : !String
            """.trimIndent(),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            model.generateProto3Schema(GenerationContext()) {}
        }

        assertEquals(
            "Proto3 model DuplicateReservedName contains duplicate reserved field name removed",
            exception.message,
        )
    }

    @Test
    fun escapesNulInReservedProtoNames() {
        val output = buildString {
            NulReservation.generateProto3Schema(GenerationContext(), ::append)
        }

        assertTrue(output.contains("reserved \"nul\\x00name\";"))
    }

    @Test
    fun rejectsPropertiesUsingReservedProtoNamesOrNumbers() {
        for ((reservation, expected) in listOf(
            "reservedIndices: [1]" to "Proto3 field value uses reserved number 1 in model ReservedField",
            "reservedNames: [value]" to "Proto3 field value uses reserved name value in model ReservedField",
        )) {
            val model = rootModel(
                """
                name: ReservedField
                $reservation
                ? 1: value
                : !String
                """.trimIndent(),
            )

            val exception = assertFailsWith<IllegalArgumentException> {
                model.generateProto3Schema(GenerationContext()) {}
            }

            assertEquals(expected, exception.message)
        }
    }

    @Test
    fun rejectsMultiTypeCasesCollidingAfterProtoFieldTransformation() {
        val model = rootModel(
            """
            name: TransformedFields
            ? 1: choice
            : !MultiType
              typeEnum:
                name: Choice
                cases:
                  ? 1: URL
                  : !String
                  ? 2: uRL
                  : !String
            """.trimIndent(),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            model.generateProto3Schema(GenerationContext()) {}
        }

        assertEquals(
            "Proto3 multi type Choice cases URL and uRL both generate field uRL",
            exception.message,
        )
    }

    @Test
    fun rejectsNestedHelpersCollidingAfterProtoNameTransformation() {
        val model = rootModel(
            """
            name: HelperCollision
            ? 1: status
            : !Enum
              enum:
                name: ChoiceType
                cases:
                  1: Active
            ? 2: choice
            : !MultiType
              typeEnum:
                name: Choice
                cases:
                  ? 1: text
                  : !String
            """.trimIndent(),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            model.generateProto3Schema(GenerationContext()) {}
        }

        assertEquals(
            "Proto3 properties status and choice both generate nested symbol ChoiceType in model HelperCollision",
            exception.message,
        )
    }

    @Test
    fun rejectsFieldsCollidingWithNestedEnumValues() {
        val model = rootModel(
            """
            name: EnumValueCollision
            ? 1: kind
            : !Enum
              enum:
                name: Kind
                cases:
                  1: status
            ? 2: status
            : !String
            """.trimIndent(),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            model.generateProto3Schema(GenerationContext()) {}
        }

        assertEquals(
            "Proto3 field status collides with nested symbol status generated for property kind in model EnumValueCollision",
            exception.message,
        )
    }

    @Test
    fun decimalUsesExactStringType() {
        val output = buildString {
            DecimalGeneratorModel.generateProto3Schema(GenerationContext()) {
                append(it)
            }
        }

        assertEquals(
            """
            message DecimalGeneratorModel {
              string amount = 1;
            }
            """.trimIndent(),
            output,
        )
    }

    @Test
    fun testDataModelConversion() {
        val output = buildString {
            CompleteMarykModel.generateProto3Schema(
                GenerationContext(
                    enums = mutableListOf(MarykTypeEnum)
                )
            ) {
                append(it)
            }
        }

        assertEquals(generatedProto3ForCompleteMarykModel, output)
    }

    @Test
    fun testNumericModelConversion() {
        val output = buildString {
            NumericMarykModel.generateProto3Schema(
                GenerationContext()
            ) {
                append(it)
            }
        }

        assertEquals(generatedProto3ForNumericMarykModel, output)
    }

    @Test
    fun testSimpleDataModelConversion() {
        val output = buildString {
            SimpleMarykModel.generateProto3Schema(
                GenerationContext(
                    enums = mutableListOf(MarykTypeEnum)
                )
            ) {
                append(it)
            }
        }

        assertEquals(generatedProto3ForSimpleMarykModel, output)
    }

    @Test
    fun testEmbeddedMarykModelConversion() {
        val output = buildString {
            EmbeddedMarykModel.generateProto3Schema(
                GenerationContext(
                    enums = mutableListOf(MarykTypeEnum)
                )
            ) {
                append(it)
            }
        }

        assertEquals(generatedProto3ForEmbeddedMarykModel, output)
    }

    @Test
    fun testTestMarykModelConversion() {
        val output = buildString {
            TestMarykModel.generateProto3Schema(
                GenerationContext(
                    enums = mutableListOf(MarykTypeEnum)
                )
            ) {
                append(it)
            }
        }

        assertEquals(generatedProto3ForTestMarykModel, output)
    }
}

private fun rootModel(yaml: String): RootDataModel<*> =
    RootDataModel.Model.Serializer.readJson(
        MarykYamlModelReader(yaml),
        DefinitionsConversionContext(),
    ).toDataObject()

private object `invalid-model` : RootDataModel<`invalid-model`>() {
    val value by string(index = 1u)
}

private object NulReservation : RootDataModel<NulReservation>(
    reservedNames = listOf("nul\u0000name"),
) {
    val value by string(index = 1u)
}

private object `invalid-embedded` : RootDataModel<`invalid-embedded`>() {
    val value by string(index = 1u)
}

private object ModelWithInvalidEmbeddedType : RootDataModel<ModelWithInvalidEmbeddedType>() {
    val embedded by embed(index = 1u, dataModel = { `invalid-embedded` })
}

private sealed class OutOfRangeMultiType<T : Any>(
    index: UInt,
    override val definition: IsUsableInMultiType<T, IsPropertyContext>?
) : IndexedEnumImpl<OutOfRangeMultiType<Any>>(index), MultiTypeEnum<T> {
    object Value : OutOfRangeMultiType<String>(536_870_912u, StringDefinition())

    companion object : MultiTypeEnumDefinition<OutOfRangeMultiType<out Any>>(
        OutOfRangeMultiType::class,
        values = { listOf(Value) },
    )
}

private object ModelWithOutOfRangeMultiType : RootDataModel<ModelWithOutOfRangeMultiType>() {
    val value by multiType(index = 1u, typeEnum = OutOfRangeMultiType)
}

private object ReservedNamesEscapingModel : RootDataModel<ReservedNamesEscapingModel>(
    reservedNames = listOf("quote\" and newline\n"),
) {
    val value by string(index = 1u)
}

package maryk.generator.proto3

import maryk.generator.kotlin.GenerationContext
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.NumericMarykModel
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

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
  int64 location = 26;
}
""".trimIndent()

class GenerateProto3ForDataModelTest {
    @Test
    fun testDataModelConversion() {
        val output = buildString {
            CompleteMarykModel.Model.generateProto3Schema(
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
            NumericMarykModel.Model.generateProto3Schema(
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
            SimpleMarykModel.Model.generateProto3Schema(
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
            EmbeddedMarykModel.Model.generateProto3Schema(
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
            TestMarykModel.Model.generateProto3Schema(
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

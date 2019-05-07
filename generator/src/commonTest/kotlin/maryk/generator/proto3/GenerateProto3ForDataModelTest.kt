package maryk.generator.proto3

import maryk.generator.kotlin.GenerationContext
import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.NumericMarykModel
import maryk.test.models.SimpleMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

val generatedProto3ForSimpleMarykModel = """
message SimpleMarykModel {
  string value = 1;
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

val generatedProto3ForCompleteMarykModel = """
message CompleteMarykModel {
  reserved 99;
  reserved "reserved";
  message MultiType {
    oneof multi {
      string o1 = 1;
      bool o2 = 2;
      repeated string o3 = 3;
    }
  }
  message MultiForKeyType {
    oneof multiForKey {
      string o1 = 1;
      bool o2 = 2;
      repeated string o3 = 3;
    }
  }
  enum MarykEnumEmbedded {
    UNKNOWN = 0;
    E1 = 1;
    E2 = 2;
    E3 = 3;
  }
  message MapWithEnumEntry {
    MarykEnumEmbedded key = 1;
    string value = 2;
  }
  string string = 1;
  uint64 number = 2;
  bool boolean = 3;
  MarykTypeEnum enum = 4;
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
  map<string, repeated string> mapWithList = 22;
  map<string, repeated string> mapWithSet = 23;
  map<string, map<string, string>> mapWithMap = 24;
}
""".trimIndent()

class GenerateProto3ForDataModelTest {
    @Test
    fun testDataModelConversion() {
        var output = ""

        CompleteMarykModel.generateProto3Schema(
            GenerationContext(
                enums = mutableListOf(MarykTypeEnum)
            )
        ) {
            output += it
        }

        output shouldBe generatedProto3ForCompleteMarykModel
    }

    @Test
    fun testNumericModelConversion() {
        var output = ""

        NumericMarykModel.generateProto3Schema(
            GenerationContext()
        ) {
            output += it
        }

        output shouldBe generatedProto3ForNumericMarykModel
    }

    @Test
    fun testSimpleDataModelConversion() {
        var output = ""

        SimpleMarykModel.generateProto3Schema(
            GenerationContext(
                enums = mutableListOf(MarykTypeEnum)
            )
        ) {
            output += it
        }

        output shouldBe generatedProto3ForSimpleMarykModel
    }
}

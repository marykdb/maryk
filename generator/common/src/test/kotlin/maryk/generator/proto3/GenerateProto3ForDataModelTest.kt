package maryk.generator.proto3

import maryk.CompleteMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class GenerateProto3ForDataModelTest {
    @Test
    fun testDataModelConversion() {
        var output = ""

        CompleteMarykModel.generateProto3Schema("maryk") {
            output += it
        }

        output shouldBe """
        syntax = "proto3";

        option java_package = "maryk";

        message CompleteMarykModel {
          message MultiType {
            oneof multi {
              string o1 = 1;
              bool o2 = 2;
            }
          }
          message MultiForKeyType {
            oneof multiForKey {
              string o1 = 1;
              bool o2 = 2;
            }
          }
          message MapWithEnumEntry {
            MarykEnumEmbedded key = 1;
            string value = 2;
          }
          optional string string = 1;
          required uint64 number = 2;
          optional bool boolean = 3;
          optional MarykEnum enum = 4;
          optional sint64 date = 5;
          optional sint64 dateTime = 6;
          optional sint32 time = 7;
          optional bytes fixedBytes = 8;
          optional bytes flexBytes = 9;
          optional bytes reference = 10;
          optional SimpleMarykModel subModel = 11;
          optional bytes valueModel = 12;
          optional repeated string list = 13;
          optional repeated sint32 set = 14;
          optional map<sint64, sint32> map = 15;
          optional MultiType multi = 16;
          required bool booleanForKey = 17;
          required sint64 dateForKey = 18;
          required MultiForKeyType multiForKey = 19;
          required MarykEnumEmbedded enumEmbedded = 20;
          optional repeated MapWithEnumEntry mapWithEnum = 21;
        }
        """.trimIndent()
    }
}

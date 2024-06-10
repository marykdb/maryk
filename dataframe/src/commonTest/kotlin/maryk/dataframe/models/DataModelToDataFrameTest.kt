package maryk.dataframe.models

import maryk.test.models.TestMarykModel
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class DataModelToDataFrameTest {
    @Test
    fun testToDataFrame() {
        val dataFrameTest = TestMarykModel.toDataFrame()

        assertEquals(7, dataFrameTest.columns().size)
        assertEquals(
            """
            |    index           name      type required final unique                                  options
            |  0     1         string    String     true false  false     [1 x 6] { regEx:ha.*, default:haha }
            |  1     2            int    Number     true false  false      [1 x 4] { type:SInt32, maxValue:6 }
            |  2     3           uint    Number     true  true  false                  [1 x 4] { type:UInt32 }
            |  3     4         double    Number     true false  false                 [1 x 4] { type:Float64 }
            |  4     5       dateTime  DateTime     true false  false                              [1 x 3] { }
            |  5     6           bool   Boolean     true  true   null                              [1 x 1] { }
            |  6     7           enum      Enum     true  true  false      [1 x 4] { enum:Option, default:V1 }
            |  7     8           list      List    false false   null [1 x 4] { valueDefinition:[1 x 4] { t...
            |  8     9            set       Set    false false   null [1 x 4] { maxSize:5, valueDefinition:...
            |  9    10            map       Map    false false   null [1 x 5] { maxSize:5, keyDefinition:[1...
            | 10    11    valueObject     Value    false false  false    [1 x 2] { dataModel:TestValueObject }
            | 11    12 embeddedValues     Embed    false false   null [1 x 2] { dataModel:EmbeddedMarykModel }
            | 12    13          multi MultiType    false false   null [1 x 3] { typeEnum:SimpleMarykTypeEnu...
            | 13    14      reference Reference    false false  false     [1 x 3] { dataModel:TestMarykModel }
            | 14    15   listOfString      List    false false   null [1 x 4] { minSize:1, maxSize:6, value...
            | 15    16  selfReference Reference    false false  false     [1 x 3] { dataModel:TestMarykModel }
            | 16    17    setOfString       Set    false false   null [1 x 4] { maxSize:6, valueDefinition:...
            | 17    18         incMap    IncMap    false false   null [1 x 4] { keyDefinition:[1 x 4] { typ...
            |
            """.trimMargin("|"),
            dataFrameTest.toString()
        )
    }
}

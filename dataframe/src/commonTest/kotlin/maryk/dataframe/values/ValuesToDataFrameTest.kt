package maryk.dataframe.values

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.types.invoke
import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykEnumEmbedded
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.SimpleMarykTypeEnum
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

class ValuesToDataFrameTest {
    @Test
    fun testToDataFrame() {
        val dataFrameTest = TestMarykModel(
            string = "haas",
            int = 4,
            uint = 53u,
            double = 3.5555,
            bool = true,
            dateTime = LocalDateTime(2017, 12, 5, 12, 40)
        ).toDataFrame()

        assertEquals(7, dataFrameTest.columns().size)
        assertEquals(
            """
            |   string int uint double         dateTime bool enum
            | 0   haas   4   53 3,5555 2017-12-05T12:40 true   V1
            |
            """.trimMargin("|"),
            dataFrameTest.toString()
        )
    }

    @Test
    fun testListToDataFrame() {
        val dataFrameTest = listOf(
            CompleteMarykModel(
                string = "Arend",
                number = 2u,
                time = LocalTime(12, 11, 10),
                booleanForKey = true,
                dateForKey = LocalDate(2019, 3, 20),
                multiForKey = S1( "test"),
                enumEmbedded = E1,
            ),
            CompleteMarykModel(
                string = "Jan",
                number = 4u,
                time = LocalTime(15, 9, 40),
                booleanForKey = false,
                dateForKey = LocalDate(2022, 5, 20),
                multiForKey = SimpleMarykTypeEnum.S2(2.toShort()),
                enumEmbedded = MarykEnumEmbedded.E2,
            ),
            CompleteMarykModel(
                string = "Marlies",
                number = 100u,
                time = LocalTime(12, 11, 10),
                booleanForKey = true,
                dateForKey = LocalDate(2002, 3, 20),
                multiForKey = S1( "Fine"),
                enumEmbedded = MarykEnumEmbedded.E3,
            )
        ).toDataFrame()

        assertEquals(25, dataFrameTest.columns().size)
        assertEquals(
            """
            |                  string      number            boolean         enum                                 date                                 dateTime                           time                  fixedBytes                flexBytes                                reference                                   subModel                               valueModel                                     list                               set                                      map                                    multi       booleanForKey                           dateForKey                              multiForKey enumEmbedded                          mapWithEnum                          mapWithList                           mapWithSet                        mapWithMap                                 location
            | 0 [Arend, Jan, Marlies] [2, 4, 100] [true, true, true] [V1, V1, V1] [2018-05-02, 2018-05-02, 2018-05-02] [2018-05-02T10:11:12, 2018-05-02T10:1... [12:11:10, 15:09:40, 12:11:10] [AAECAwQ, AAECAwQ, AAECAwQ] [AAECAw, AAECAw, AAECAw] [AAECAQAAECAQAAECAQAAEA, AAECAQAAECAQ... [       value\n 0 a default\n,        v... [ValueMarykObject(int=10, date=2010-1... [[ha1, ha2, ha3], [ha1, ha2, ha3], [h... [[1, 2, 3], [1, 2, 3], [1, 2, 3]] [{2010-11-12=1, 2011-12-13=1}, {2010-... [TypedValueImpl(type=T1, value=a valu... [true, false, true] [2019-03-20, 2022-05-20, 2002-03-20] [TypedValueImpl(type=S1, value=test),... [E1, E2, E3] [{E1=value}, {E1=value}, {E1=value}] [{a=[b, c]}, {a=[b, c]}, {a=[b, c]}] [{a=[b, c]}, {a=[b, c]}, {a=[b, c]}] [{a={b=c}}, {a={b=c}}, {a={b=c}}] [52.0906448,5.1212607, 52.0906448,5.1...
            |
            """.trimMargin("|"),
            dataFrameTest.toString()
        )
    }

}

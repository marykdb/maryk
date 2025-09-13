package maryk.dataframe.values

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.types.invoke
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.EmbeddedMarykModel.value
import maryk.test.models.MarykEnumEmbedded
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.SimpleMarykTypeEnum
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ValuesToDataFrameTest {
    init {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun testToDataFrame() {
        val dataFrameTest = TestMarykModel.create {
            string with "haas"
            int with 4
            uint with 53u
            double with 3.5555
            bool with true
            embeddedValues with EmbeddedMarykModel.create { value with "haas" }
            dateTime with LocalDateTime(2017, 12, 5, 12, 40)
        }.toDataFrame()

        assertEquals(8, dataFrameTest.columns().size)
        assertEquals(
            """
            |   string int uint double         dateTime bool enum embeddedValues
            | 0   haas   4   53 3.5555 2017-12-05T12:40 true   V1 { value:haas }
            |
            """.trimMargin("|"),
            dataFrameTest.toString()
        )
    }

    @Test
    fun testListToDataFrame() {
        val dataFrameTest = listOf(
            CompleteMarykModel.create {
                string with "Arend"
                number with 2u
                time with LocalTime(12, 11, 10)
                booleanForKey with true
                dateForKey with LocalDate(2019, 3, 20)
                multiForKey with S1( "test")
                enumEmbedded with E1
            },
            CompleteMarykModel.create {
                string with "Jan"
                number with 4u
                time with LocalTime(15, 9, 40)
                booleanForKey with false
                dateForKey with LocalDate(2022, 5, 20)
                multiForKey with SimpleMarykTypeEnum.S2(2.toShort())
                enumEmbedded with MarykEnumEmbedded.E2
            },
            CompleteMarykModel.create {
                string with "Marlies"
                number with 100u
                time with LocalTime(12, 11, 10)
                booleanForKey with true
                dateForKey with LocalDate(2002, 3, 20)
                multiForKey with S1( "Fine")
                enumEmbedded with MarykEnumEmbedded.E3
            }
        ).toDataFrame()

        assertEquals(25, dataFrameTest.columns().size)
        assertEquals(
            """
            |    string number boolean enum       date            dateTime     time fixedBytes flexBytes              reference            subModel                               valueModel            list       set                          map                                  multi booleanForKey dateForKey                         multiForKey enumEmbedded mapWithEnum mapWithList mapWithSet mapWithMap             location
            | 0   Arend      2    true   V1 2018-05-02 2018-05-02T10:11:12 12:11:10    AAECAwQ    AAECAw AAECAQAAECAQAAECAQAAEA { value:a default } ValueMarykObject(int=10, date=2010-10... [ha1, ha2, ha3] [1, 2, 3] {2010-11-12=1, 2011-12-13=1} TypedValueImpl(type=T1, value=a value)          true 2019-03-20 TypedValueImpl(type=S1, value=test)           E1  {E1=value}  {a=[b, c]} {a=[b, c]}  {a={b=c}} 52.0906448,5.1212607
            | 1     Jan      4    true   V1 2018-05-02 2018-05-02T10:11:12 15:09:40    AAECAwQ    AAECAw AAECAQAAECAQAAECAQAAEA { value:a default } ValueMarykObject(int=10, date=2010-10... [ha1, ha2, ha3] [1, 2, 3] {2010-11-12=1, 2011-12-13=1} TypedValueImpl(type=T1, value=a value)         false 2022-05-20    TypedValueImpl(type=S2, value=2)           E2  {E1=value}  {a=[b, c]} {a=[b, c]}  {a={b=c}} 52.0906448,5.1212607
            | 2 Marlies    100    true   V1 2018-05-02 2018-05-02T10:11:12 12:11:10    AAECAwQ    AAECAw AAECAQAAECAQAAECAQAAEA { value:a default } ValueMarykObject(int=10, date=2010-10... [ha1, ha2, ha3] [1, 2, 3] {2010-11-12=1, 2011-12-13=1} TypedValueImpl(type=T1, value=a value)          true 2002-03-20 TypedValueImpl(type=S1, value=Fine)           E3  {E1=value}  {a=[b, c]} {a=[b, c]}  {a={b=c}} 52.0906448,5.1212607
            |
            """.trimMargin("|"),
            dataFrameTest.toString()
        )
    }

}

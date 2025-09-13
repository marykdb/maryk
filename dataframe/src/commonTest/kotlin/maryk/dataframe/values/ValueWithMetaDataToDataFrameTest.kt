package maryk.dataframe.values

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.models.key
import maryk.core.properties.types.invoke
import maryk.core.query.ValuesWithMetaData
import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykEnumEmbedded
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.SimpleMarykTypeEnum
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ValueWithMetaDataToDataFrameTest {
    init {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun testToDataFrame() {
        val data = TestMarykModel.create {
            string with "haas"
            int with 4
            uint with 53u
            double with 3.5555
            bool with true
            dateTime with LocalDateTime(2017, 12, 5, 12, 40)
        }
        val dataFrameTest = ValuesWithMetaData(
            key = TestMarykModel.key("AAAANQEAAQ"),
            values = data,
            isDeleted = false,
            firstVersion = 0uL,
            lastVersion = 3uL
        ).toDataFrame()

        assertEquals(5, dataFrameTest.columns().size)
        assertEquals(
            """
            |          key                                   values isDeleted firstVersion lastVersion
            | 0 AAAANQEAAQ { string:haas, int:4, uint:53, double...     false            0           3
            |
            """.trimMargin("|"),
            dataFrameTest.toString()
        )
    }

    @Test
    fun testListToDataFrame() {
        val dataElements = listOf(
            CompleteMarykModel.key("20YT3ZPVQa4HyI01mEbRhgABAX__ucg") to CompleteMarykModel.create {
                string with "Arend"
                number with 2u
                time with LocalTime(12, 11, 10)
                booleanForKey with true
                dateForKey with LocalDate(2019, 3, 20)
                multiForKey with S1( "test")
                enumEmbedded with E1
            },
            CompleteMarykModel.key("8snIHM7kQBApBRl-NiCXiwACAH__tUM") to CompleteMarykModel.create {
                string with "Jan"
                number with 4u
                time with LocalTime(15, 9, 40)
                booleanForKey with false
                dateForKey with LocalDate(2022, 5, 20)
                multiForKey with SimpleMarykTypeEnum.S2(2.toShort())
                enumEmbedded with MarykEnumEmbedded.E2
            },
            CompleteMarykModel.key("a8QqUwtIQvACmV_yygoLpAABAX__0gk") to  CompleteMarykModel.create {
                string with "Marlies"
                number with 100u
                time with LocalTime(12, 11, 10)
                booleanForKey with true
                dateForKey with LocalDate(2002, 3, 20)
                multiForKey with S1( "Fine")
                enumEmbedded with MarykEnumEmbedded.E3
            }
        )

        val dataFrameTest = dataElements.map {
            ValuesWithMetaData(
                key = it.first,
                values = it.second,
                isDeleted = false,
                firstVersion = 5uL,
                lastVersion = 8uL
            )
        }.toDataFrame()

        assertEquals(5, dataFrameTest.columns().size)
        assertEquals(
            """
            |                               Key                                   Values IsDeleted FirstVersion LastVersion
            | 0 20YT3ZPVQa4HyI01mEbRhgABAX__ucg { string:Arend, number:2, boolean:tru...     false            5           8
            | 1 8snIHM7kQBApBRl-NiCXiwACAH__tUM { string:Jan, number:4, boolean:true,...     false            5           8
            | 2 a8QqUwtIQvACmV_yygoLpAABAX__0gk { string:Marlies, number:100, boolean...     false            5           8
            |
            """.trimMargin("|"),
            dataFrameTest.toString()
        )
    }

}

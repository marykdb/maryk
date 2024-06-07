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
import kotlin.test.Test
import kotlin.test.assertEquals

class ValueWithMetaDataToDataFrameTest {
    @Test
    fun testToDataFrame() {
        val data = TestMarykModel(
            string = "haas",
            int = 4,
            uint = 53u,
            double = 3.5555,
            bool = true,
            dateTime = LocalDateTime(2017, 12, 5, 12, 40)
        )
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
            |          Key                                     Data IsDeleted FirstVersion LastVersion
            | 0 AAAANQEAAQ [1 x 7] { string:haas, int:4, uint:53...     false            0           3
            |
            """.trimMargin("|"),
            dataFrameTest.toString()
        )
    }

    @Test
    fun testListToDataFrame() {
        val dataElements = listOf(
            CompleteMarykModel.key("20YT3ZPVQa4HyI01mEbRhgABAX__ucg") to CompleteMarykModel(
                string = "Arend",
                number = 2u,
                time = LocalTime(12, 11, 10),
                booleanForKey = true,
                dateForKey = LocalDate(2019, 3, 20),
                multiForKey = S1( "test"),
                enumEmbedded = E1,
            ),
            CompleteMarykModel.key("8snIHM7kQBApBRl-NiCXiwACAH__tUM") to CompleteMarykModel(
                string = "Jan",
                number = 4u,
                time = LocalTime(15, 9, 40),
                booleanForKey = false,
                dateForKey = LocalDate(2022, 5, 20),
                multiForKey = SimpleMarykTypeEnum.S2(2.toShort()),
                enumEmbedded = MarykEnumEmbedded.E2,
            ),
            CompleteMarykModel.key("a8QqUwtIQvACmV_yygoLpAABAX__0gk") to  CompleteMarykModel(
                string = "Marlies",
                number = 100u,
                time = LocalTime(12, 11, 10),
                booleanForKey = true,
                dateForKey = LocalDate(2002, 3, 20),
                multiForKey = S1( "Fine"),
                enumEmbedded = MarykEnumEmbedded.E3,
            )
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
            |                                        Key                                   Values             IsDeleted FirstVersion LastVersion
            | 0 [20YT3ZPVQa4HyI01mEbRhgABAX__ucg, 8sn... [   string number boolean enum       ... [false, false, false]    [5, 5, 5]   [8, 8, 8]
            |
            """.trimMargin("|"),
            dataFrameTest.toString()
        )
    }

}

package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import kotlin.test.Test
import kotlin.test.expect

internal class IndexChangeTest {
    private val indexChange = IndexChange(
        listOf(
            IndexUpdate(
                index = byteArrayOf(0, 1),
                indexKey = byteArrayOf(1, 2),
                previousIndexKey = byteArrayOf(2, 3)
            ),
            IndexDelete(
                index = byteArrayOf(3, 4),
                indexKey = byteArrayOf(5, 6)
            )
        )
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.indexChange, IndexChange)
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.indexChange, IndexChange)
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            - !Update
              index: AAE
              indexKey: AQI
              previousIndexKey: AgM
            - !Delete
              index: AwQ
              indexKey: BQY

            """.trimIndent()
        ) {
            checkYamlConversion(this.indexChange, IndexChange)
        }
    }
}

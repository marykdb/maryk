package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.Bytes
import kotlin.test.Test
import kotlin.test.expect

internal class IndexChangeTest {
    private val indexChange = IndexChange(
        listOf(
            IndexUpdate(
                index = Bytes(byteArrayOf(0, 1)),
                indexKey = Bytes(byteArrayOf(1, 2)),
                previousIndexKey = Bytes(byteArrayOf(2, 3))
            ),
            IndexDelete(
                index = Bytes(byteArrayOf(3, 4)),
                indexKey = Bytes(byteArrayOf(5, 6))
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

package maryk.core.services.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import kotlin.test.Test
import kotlin.test.expect

class CloseListenerTest {
    private val closeListener = CloseListener(12345uL)

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.closeListener, CloseListener)
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "id": "12345"
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.closeListener, CloseListener)
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            id: 12345

            """.trimIndent()
        ) {
            checkYamlConversion(this.closeListener, CloseListener)
        }
    }
}

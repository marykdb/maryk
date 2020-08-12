package maryk.core.services.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import kotlin.test.Test
import kotlin.test.expect

class RegisteredListenerTest {
    private val registeredListener = RegisteredListener(12345uL)

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.registeredListener, RegisteredListener)
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
            checkJsonConversion(this.registeredListener, RegisteredListener)
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            id: 12345

            """.trimIndent()
        ) {
            checkYamlConversion(this.registeredListener, RegisteredListener)
        }
    }
}

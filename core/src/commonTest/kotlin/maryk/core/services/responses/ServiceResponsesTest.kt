package maryk.core.services.responses

import maryk.lib.extensions.toHex
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceResponsesTest {
    val serviceResponses = ServiceResponses(
        mapOf(
            1u to RegisteredListener
        )
    )

    @Test
    fun getTypedResponse() {
        val response = RegisteredListener(1uL)
        serviceResponses.getTypedResponse(response).apply {
            assertEquals(response, this.value)
        }
    }

    @Test
    fun convertToTransportByteArray() {
        val response = RegisteredListener(1uL)
        assertEquals("0a020801", serviceResponses.toTransportByteArray(response).toHex())
    }
}

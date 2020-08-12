package maryk.core.services.requests

import maryk.lib.extensions.toHex
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceRequestsTest {
    private val serviceRequests = ServiceRequests(mapOf(
        1u to CloseListener
    ))

    @Test
    fun getTypedRequest() {
        val request = CloseListener(1uL)
        serviceRequests.getTypedRequest(request).apply {
            assertEquals(request, this.value)
        }
    }

    @Test
    fun convertToTransportByteArray() {
        val request = CloseListener(1uL)
        assertEquals("0a020801", serviceRequests.toTransportByteArray(request).toHex())
    }
}

package maryk.core.clock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

class HLCTest {
    @Test
    fun convertToAndFromLogicalAndPhysical() {
        val physical = 12345678uL
        val logical = 1234u

        val hlc = HLC(
            physical,
            logical
        )

        assertEquals(12945381655762uL, hlc.timestamp)

        assertEquals(logical, hlc.toLogicalTime())
        assertEquals(physical, hlc.toPhysicalUnixTime())
    }

    @Test
    fun incrementLogicalTime() {
        val hlc = HLC(12945381655762uL)

        val newHCL = hlc.increment()

        assertEquals(12945381655763uL, newHCL.timestamp)

        assertEquals(1235u, newHCL.toLogicalTime())
    }

    @Test
    fun maxCalculations() {
        val physical = 12345678uL
        val logical = 1234u

        val hlc = HLC(physical, logical)

        val matchingPhysicalTime = HLC(physical)

        expect(HLC(physical, logical + 1u)) {
            hlc.calculateMaxTimeStamp(
                HLC(physical - 2uL, logical - 2u)
            ) { matchingPhysicalTime }
        }

        expect(HLC(physical, logical + 3u)) {
            hlc.calculateMaxTimeStamp(
                HLC(physical, logical + 2u)
            ) { matchingPhysicalTime }
        }

        val newTime = HLC(physical + 1u, 0u)

        expect(newTime) {
            hlc.calculateMaxTimeStamp(
                HLC(physical, logical + 2u)
            ) { newTime }
        }
    }
}

package maryk.datastore.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.string
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.query.pairs.with
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private object FingerprintBufferRecord : RootDataModel<FingerprintBufferRecord>(name = "FingerprintBufferRecord") {
    val text by string(1u)
    val stamp by dateTime(2u, required = false, precision = NANOS)
}

class AtomicReceiptBufferTest {
    private data class Fixture(val size: Int, val stamp: Boolean, val current: String, val released: String)
    // Captured from the accepted separate-buffer encoder before this change.
    private val fixtures = listOf(
        Fixture(0, false, "f974ed6edf0aa5a5da3ced69ed0ca9674efc7e0a793c31ea29e2f5cc0fc0b3e7", "f974ed6edf0aa5a5da3ced69ed0ca9674efc7e0a793c31ea29e2f5cc0fc0b3e7"),
        Fixture(0, true, "ed06770821d21f5a8221b35f53b9dd356a56dfed594ead475ac569d2d50792f5", "697e16479e47a298196958a60353f62fb584032cce93cad634fd8a7befa18a3c"),
        Fixture(513, false, "4c77c71349808dcb5771684000b3416582baceac2587cfdab12875ac0db192f1", "4c77c71349808dcb5771684000b3416582baceac2587cfdab12875ac0db192f1"),
        Fixture(513, true, "9f0325e438b7e4f57de63b681bf60cd2c295a9e0273257aea9d8a42b2b5b691e", "6ad44e5001bee0f607cb1eb91881e52bfa436cb0450c0069bb84d138689fc705"),
        Fixture(32768, false, "0d11c454ace5cab3e31c3c6ba5789a15ef645585f5cd4bf37ca68776f52b1137", "0d11c454ace5cab3e31c3c6ba5789a15ef645585f5cd4bf37ca68776f52b1137"),
        Fixture(32768, true, "73af2b0620ea9d4c5da93b0595db398b91268f12a32ea18bc7ff03608cf80a6c", "0978554ef21911da6bfd508397293c891ec74de2249c0da64ba3cd7cb87e93b1")
    )
    private val codec = AtomicReceiptCodec(mapOf(1u to FingerprintBufferRecord))

    private fun request(fixture: Fixture) = TransactionRequest("buffer-${fixture.size}-${fixture.stamp}",
        listOf(FingerprintBufferRecord.add(
            FingerprintBufferRecord.key(ByteArray(16)) to FingerprintBufferRecord.create {
                text with ("é医💊" + "x".repeat(fixture.size))
                if (fixture.stamp) stamp with LocalDateTime(1970, 1, 1, 0, 0, 8, 1)
            },
        )), commit = true)

    @Test
    fun fingerprintsMatchAcceptedUnicodeAndTimestampFixtures() = runTest {
        for (fixture in fixtures) {
            val request = request(fixture)
            val fingerprints = codec.transactionFingerprints(request)
            assertEquals(fixture.current, fingerprints.current.toHexString())
            assertEquals(fixture.released, fingerprints.released?.toHexString())
            assertEquals(fixture.current, codec.transactionFingerprint(request).toHexString())
            if (!fixture.stamp) assertSame(fingerprints.current, fingerprints.released)
        }
    }

    @Test
    fun concurrentFingerprintsKeepTheirPrivateBuffersIndependent() = runTest {
        coroutineScope {
            List(24) { index -> async(Dispatchers.Default) {
                val fixture = fixtures[index % fixtures.size]
                val fingerprints = codec.transactionFingerprints(request(fixture))
                assertEquals(fixture.current, fingerprints.current.toHexString())
                assertEquals(fixture.released, fingerprints.released?.toHexString())
            } }.awaitAll()
        }
    }
}

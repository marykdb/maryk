package maryk.core.models.serializers

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import maryk.core.models.DataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list

private object ConcurrentFragments : DataModel<ConcurrentFragments>() {
    val items by list(1u, valueDefinition = StringDefinition())
}

class ProtoBufContinuationConcurrencyTest {
    @Test
    fun concurrentReadsAndFailuresKeepTheirFramesAndValuesIsolated() {
        val definition = EmbeddedValuesDefinition(dataModel = { ConcurrentFragments })
        val workers = Executors.newFixedThreadPool(8)
        val barrier = CyclicBarrier(8)
        try {
            val futures = (0 until 8).map { worker -> workers.submit(Callable {
                barrier.await(30, TimeUnit.SECONDS)
                repeat(80) { iteration ->
                    val id = "$worker:$iteration"
                    val previous = ConcurrentFragments.create { items with listOf(id) }
                    val incoming = ConcurrentFragments.create { items with listOf("next:$id") }
                    val bytes = definition.toTransportByteArray(incoming)
                    var offset = 0
                    val read = definition.readTransportBytes(bytes.size, { bytes[offset++] }, earlierValue = previous)
                    assertEquals(listOf(id, "next:$id"), read.get { items })
                    assertEquals(listOf(id), previous.get { items })
                    assertTrue(protoBufContinuations.isEmpty())
                    assertFailsWith<IllegalStateException> {
                        definition.readTransportBytes(1, { error("Simulated reader failure") }, earlierValue = previous)
                    }
                    assertTrue(protoBufContinuations.isEmpty())
                }
            }) }
            futures.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            workers.shutdownNow()
        }
        assertTrue(protoBufContinuations.isEmpty())
    }
}

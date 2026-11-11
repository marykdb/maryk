package maryk.datastore.shared.benchmark

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.datastore.shared.Cache
import kotlin.system.measureNanoTime

private val key = Key<IsValuesDataModel>(byteArrayOf(1))
private fun reference() = object : IsPropertyReferenceForCache<String, StringDefinition> {
    override val propertyDefinition = StringDefinition()
}
private interface ProbeCache {
    fun read(reference: IsPropertyReferenceForCache<*, *>, reader: () -> Any?): Any?
}
private fun cache(previous: Boolean): ProbeCache = if (previous) object : ProbeCache {
    val cache = EntryCountCache()
    override fun read(reference: IsPropertyReferenceForCache<*, *>, reader: () -> Any?) = cache.readValue(1u, key, reference, 1uL, reader)
} else object : ProbeCache {
    val cache = Cache()
    override fun read(reference: IsPropertyReferenceForCache<*, *>, reader: () -> Any?) = cache.readValue(1u, key, reference, 1uL, reader)
}

private data class Scenario(val name: String, val references: Int, val bytes: Int, val operations: Int = 20_000)
private data class Sample(val ns: Double, val allocated: Double, val misses: Int)

/** Opt-in warmed comparisons. Timings are diagnostic, never test assertions. */
fun main(args: Array<String>) {
    if (args.contentEquals(arrayOf("request"))) { runRequestCachePerformanceProbe(); return }
    require(args.isEmpty()) { "Expected no arguments or 'request'" }
    val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
    if (bean?.isThreadAllocatedMemorySupported == true) bean.isThreadAllocatedMemoryEnabled = true
    fun allocated(): Long = if (bean?.isThreadAllocatedMemorySupported == true) bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) else -1
    for (scenario in listOf(
        Scenario("hot-small", 32, 32, 200_000),
        Scenario("churn-small", 256, 32),
        Scenario("hot-fitting", 1, 32_768),
        Scenario("wide-medium", 128, 4096, 4000),
        Scenario("oversized", 1, 98_304, 400),
    )) {
        val samples = mutableMapOf<Boolean, MutableList<Sample>>()
        val source = ByteArray(scenario.bytes) { 'x'.code.toByte() }
        val references = List(scenario.references) { reference() }
        repeat(9) { round ->
            for (previous in if (round % 2 == 0) listOf(true, false) else listOf(false, true)) {
                val cache = cache(previous)
                // Warm this cache and its access paths before collecting steady-state samples.
                repeat(maxOf(1000, scenario.references * 2)) { index -> cache.read(references[index % references.size]) { source.decodeToString() } }
                var misses = 0
                val before = allocated()
                val elapsed = measureNanoTime {
                    repeat(scenario.operations) { index ->
                        val value = cache.read(references[index % references.size]) { misses++; source.decodeToString() }
                        check((value as String).length == scenario.bytes)
                    }
                }
                val after = allocated()
                if (round >= 3) samples.getOrPut(previous) { mutableListOf() } += Sample(
                    elapsed.toDouble() / scenario.operations,
                    if (before < 0 || after < 0) -1.0 else (after - before).toDouble() / scenario.operations,
                    misses,
                )
            }
        }
        for ((previous, values) in samples) println("CACHE workload=${scenario.name} policy=${if (previous) "before" else "after"} ns_per_read=${values.map { it.ns }.sorted()[values.size / 2]} allocated_per_read=${values.map { it.allocated }.sorted()[values.size / 2]} misses=${values.map { it.misses }.sorted()[values.size / 2]}/${scenario.operations}")
    }
    for (bytes in listOf(32, 4096, 65_536, 98_304)) for (previous in listOf(true, false)) retainedPayload(previous, bytes)
}

private fun retainedPayload(previous: Boolean, bytes: Int) {
    val cache = cache(previous)
    val references = List(128) { reference() }
    val values = mutableListOf<WeakReference<String>>()
    val source = ByteArray(bytes) { 'x'.code.toByte() }
    fun settledHeap(): Long {
        repeat(4) { System.gc(); Thread.sleep(25) }
        return ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
    }
    val before = settledHeap()
    for (reference in references) cache.read(reference) {
        source.decodeToString().also { values += WeakReference(it) }
    }
    val after = settledHeap()
    val retained = values.count { it.get() != null }
    Reference.reachabilityFence(cache)
    Reference.reachabilityFence(references)
    Reference.reachabilityFence(source)
    println("CACHE_RETENTION value_chars=$bytes policy=${if (previous) "before" else "after"} retained_values=$retained/128 utf16_payload_bytes=${retained * bytes * 2L} heap_delta_bytes=${after - before}")
}

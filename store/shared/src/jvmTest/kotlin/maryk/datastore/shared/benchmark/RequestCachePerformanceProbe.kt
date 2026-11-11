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
import maryk.datastore.shared.readStoredValue
import maryk.datastore.shared.readValue
import kotlin.system.measureNanoTime

private val requestProbeKey = Key<IsValuesDataModel>(byteArrayOf(1))
private fun requestReference() = object : IsPropertyReferenceForCache<String, StringDefinition> {
    override val propertyDefinition = StringDefinition()
}
private interface RequestProbeCache {
    fun read(reference: IsPropertyReferenceForCache<*, *>, reader: () -> Any?): Any?
}
private fun requestProbeCache(previous: Boolean): RequestProbeCache = if (previous) object : RequestProbeCache {
    val cache = Cache()
    override fun read(reference: IsPropertyReferenceForCache<*, *>, reader: () -> Any?) = cache.readValue(1u, requestProbeKey, reference, 1uL, reader)
} else object : RequestProbeCache {
    val cache = Cache.forRequest()
    override fun read(reference: IsPropertyReferenceForCache<*, *>, reader: () -> Any?) = cache.readValue(1u, requestProbeKey, reference, 1uL, reader)
}

private data class RequestScenario(val name: String, val references: Int, val bytes: Int, val operations: Int = 20_000)
private data class RequestSample(val ns: Double, val allocated: Double, val misses: Int)

/** Opt-in warmed comparisons. Timings are diagnostic, never test assertions. */
internal fun runRequestCachePerformanceProbe() {
    val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
    if (bean?.isThreadAllocatedMemorySupported == true) bean.isThreadAllocatedMemoryEnabled = true
    fun allocated(): Long = if (bean?.isThreadAllocatedMemorySupported == true) bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) else -1
    for (scenario in listOf(
        RequestScenario("hot-small", 32, 32, 200_000),
        RequestScenario("churn-small", 256, 32),
        RequestScenario("hot-fitting", 1, 32_768),
        RequestScenario("wide-medium", 128, 4096, 4000),
        RequestScenario("oversized", 1, 98_304, 400),
    )) {
        val samples = mutableMapOf<Boolean, MutableList<RequestSample>>()
        val source = ByteArray(scenario.bytes) { 'x'.code.toByte() }
        val references = List(scenario.references) { requestReference() }
        repeat(9) { round ->
            for (previous in if (round % 2 == 0) listOf(true, false) else listOf(false, true)) {
                val cache = requestProbeCache(previous)
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
                if (round >= 3) samples.getOrPut(previous) { mutableListOf() } += RequestSample(
                    elapsed.toDouble() / scenario.operations,
                    if (before < 0 || after < 0) -1.0 else (after - before).toDouble() / scenario.operations,
                    misses,
                )
            }
        }
        for ((previous, values) in samples) println("REQUEST_CACHE workload=${scenario.name} policy=${if (previous) "strict" else "pooled"} ns_per_read=${values.map { it.ns }.sorted()[values.size / 2]} allocated_per_read=${values.map { it.allocated }.sorted()[values.size / 2]} misses=${values.map { it.misses }.sorted()[values.size / 2]}/${scenario.operations}")
    }
    for (bytes in listOf(32, 4096, 65_536, 98_304)) for (previous in listOf(true, false)) retainedRequestPayload(previous, bytes)
    manyKeyRetention(false)
    manyKeyRetention(true)
    boundedDecoderProbe()
}

private fun retainedRequestPayload(previous: Boolean, bytes: Int) {
    val cache = requestProbeCache(previous)
    val references = List(128) { requestReference() }
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
    println("REQUEST_CACHE_RETENTION value_chars=$bytes policy=${if (previous) "strict" else "pooled"} retained_values=$retained/128 utf16_payload_bytes=${retained * bytes * 2L} heap_delta_bytes=${after - before}")
}

private fun manyKeyRetention(pooled: Boolean) {
    val cache = if (pooled) Cache.forRequest() else Cache()
    val keys = List(256) { Key<IsValuesDataModel>(byteArrayOf(it.toByte(), (it shr 8).toByte())) }
    val references = List(16) { requestReference() }
    val values = ArrayList<WeakReference<String>>()
    val source = ByteArray(4096) { 'x'.code.toByte() }
    fun heap(): Long {
        repeat(4) { System.gc(); Thread.sleep(25) }
        return ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
    }
    val before = heap()
    for (key in keys) for (reference in references) cache.readValue(1u, key, reference, 1uL) {
        source.decodeToString().also { values += WeakReference(it) }
    }
    val after = heap()
    val retained = values.count { it.get() != null }
    Reference.reachabilityFence(cache)
    Reference.reachabilityFence(keys)
    Reference.reachabilityFence(references)
    Reference.reachabilityFence(source)
    println("REQUEST_MANY_KEYS policy=${if (pooled) "pooled" else "strict"} retained_values=$retained/4096 heap_delta_bytes=${after-before}")
}

private fun boundedDecoderProbe() {
    val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
    if (bean?.isThreadAllocatedMemorySupported == true) bean.isThreadAllocatedMemoryEnabled = true
    fun allocated() = if (bean?.isThreadAllocatedMemorySupported == true) bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) else -1L
    val definition = StringDefinition()
    for (size in listOf(32, 32_768, 98_304)) {
        val payload = byteArrayOf(1) + ByteArray(size) { 'x'.code.toByte() }
        val operations = if (size == 32) 20_000 else 200
        val timings = mutableMapOf<Boolean, MutableList<Double>>()
        val allocations = mutableMapOf<Boolean, MutableList<Double>>()
        repeat(9) { round ->
            for (bounded in if (round % 2 == 0) listOf(false, true) else listOf(true, false)) {
                fun decode(): Any? {
                    if (bounded) return readStoredValue(definition, payload, 0, payload.size)
                    var index = 0
                    return readValue(definition, { payload[index++] }) { payload.size - index }
                }
                repeat(100) { check((decode() as String).length == size) }
                val before = allocated()
                val elapsed = measureNanoTime { repeat(operations) { check((decode() as String).length == size) } }
                val after = allocated()
                if (round >= 3) {
                    timings.getOrPut(bounded) { mutableListOf() } += elapsed.toDouble() / operations
                    allocations.getOrPut(bounded) { mutableListOf() } += if (before < 0 || after < 0) -1.0 else (after - before).toDouble() / operations
                }
            }
        }
        for ((bounded, samples) in timings) println("BOUNDED_DECODER chars=$size bounded=$bounded ns_per_value=${samples.sorted()[samples.size / 2]} allocated_per_value=${allocations.getValue(bounded).sorted()[samples.size / 2]}")
    }
}

package maryk.datastore.shared

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValueImpl
import maryk.core.properties.types.MutableTypedValue
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.Values
import maryk.core.values.copyWithOwnedValues

/** Memoizes only repeated Get keys, within one snapshot/attempt and one projection.
 * Payloads are already retained by the response. Hits copy built-in mutable containers. */
class RequestRecordCache<DM : IsRootDataModel> private constructor(private val repeated: Set<Key<DM>>) {
    private var maxValueNodes = 4096
    private val records = HashMap<Key<DM>, ValuesWithMetaData<DM>?>()
    private var rejected: MutableSet<Key<DM>>? = null

    fun contains(key: Key<DM>): Boolean = records.containsKey(key)

    fun get(key: Key<DM>): ValuesWithMetaData<DM>? = records[key]?.let {
        it.copy(values = it.values.copyWithOwnedValues())
    }

    fun put(key: Key<DM>, value: ValuesWithMetaData<DM>?) {
        if (key !in repeated) return
        if (value == null) {
            records[key] = null
            return
        }
        if (rejected?.contains(key) == true) return
        // Unknown/custom graphs retain the original decoding path rather than sharing ownership.
        if (canCopy(value.values, intArrayOf(maxValueNodes), 0)) {
            records[key] = value
        } else {
            // One snapshot/projection cannot make the same graph eligible on a later duplicate.
            val skipped = rejected ?: HashSet<Key<DM>>().also { rejected = it }
            skipped.add(key)
        }
    }

    companion object {
        internal const val MAX_KEYS = 4096
        /** Bound planning metadata; very large requests retain their existing streaming path. */
        fun <DM : IsRootDataModel> create(keys: List<Key<DM>>): RequestRecordCache<DM>? {
            if (keys.size !in 2..MAX_KEYS) return null
            val seen = HashSet<Key<DM>>()
            val repeated = HashSet<Key<DM>>()
            for (key in keys) if (!seen.add(key)) repeated.add(key)
            return if (repeated.isEmpty()) null else RequestRecordCache(repeated)
        }

        /** Opt into larger graphs while retaining bounded ownership checks and the existing default. */
        fun <DM : IsRootDataModel> create(keys: List<Key<DM>>, maxValueNodes: Int): RequestRecordCache<DM>? {
            require(maxValueNodes in 1..16_384) { "Copy work must be bounded to 1..16384 value nodes" }
            return create(keys)?.also { it.maxValueNodes = maxValueNodes }
        }

        private fun canCopy(value: Any?, remaining: IntArray, depth: Int): Boolean {
            if (--remaining[0] < 0 || depth > 64) return false
            return when (value) {
                is Values<*> -> value.all { canCopy(it.value, remaining, depth + 1) }
                is TypedValueImpl<*, *>, is MutableTypedValue<*, *> -> canCopy(value.value, remaining, depth + 1)
                is List<*> -> value.all { canCopy(it, remaining, depth + 1) }
                is Set<*> -> value.all { canCopy(it, remaining, depth + 1) }
                is Map<*, *> -> value.all { (key, item) -> isScalar(key) && canCopy(item, remaining, depth + 1) }
                is ByteArray -> true
                else -> isScalar(value)
            }
        }

        private fun isScalar(value: Any?): Boolean = when (value) {
            is BooleanArray, is CharArray, is ShortArray, is IntArray,
            is LongArray, is FloatArray, is DoubleArray, is ByteArray -> false
            else -> estimatedCacheValueBytes(value) >= 0
        }
    }
}

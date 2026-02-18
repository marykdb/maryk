package maryk.datastore.memory.records

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.core.processors.datastore.matchers.IsQualifierMatcher
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapAnyKeyReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.references.SetAnyValueReference
import maryk.core.properties.references.SimpleTypedValueReference
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.values.IsValuesGetter
import maryk.datastore.memory.processors.changers.getValue
import maryk.datastore.memory.processors.changers.getValueAtIndex
import maryk.datastore.memory.processors.objectSoftDeleteQualifier
import maryk.lib.extensions.compare.compareTo

/**
 * A DataRecord stored at [key] with [values]
 * [firstVersion] and [lastVersion] signify the versions of first and last change
 * [isDeleted] is a state switch to signify record was deleted
 */
internal data class DataRecord<DM : IsRootDataModel>(
    val key: Key<DM>,
    var values: List<DataRecordNode>,
    val firstVersion: HLC,
    var lastVersion: HLC
) : IsValuesGetter {
    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
        propertyReference: IsPropertyReference<T, D, C>
    ): T? {
        if (propertyReference is IsMapReference<*, *, *, *>) {
            @Suppress("UNCHECKED_CAST")
            return readMapValue(propertyReference as IsMapReference<Any, Any, IsPropertyContext, *>?) as T?
        }
        if (propertyReference is SetReference<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return readSetValue(propertyReference as SetReference<Any, IsPropertyContext>?) as T?
        }
        return getValue<T>(this.values, propertyReference.toStorageByteArray())?.value
    }

    private fun readMapValue(
        propertyReference: IsMapReference<Any, Any, IsPropertyContext, *>?
    ): Map<Any, Any>? {
        if (propertyReference == null) return null
        val mapDefinition = propertyReference.propertyDefinition.definition
        val mapPrefix = propertyReference.toStorageByteArray()
        val map = linkedMapOf<Any, Any>()

        for (index in values.indices) {
            val node = getValueAtIndex<Any>(values, index) ?: continue
            val reference = node.reference
            if (reference.size <= mapPrefix.size) continue
            if (!reference.copyOfRange(0, mapPrefix.size).contentEquals(mapPrefix)) continue

            val key = try {
                var readIndex = mapPrefix.size
                val mapKeyLength = initIntByVar { reference[readIndex++] }
                val keyValue = mapDefinition.keyDefinition.readStorageBytes(mapKeyLength) { reference[readIndex++] }
                if (readIndex != reference.size) continue
                keyValue
            } catch (_: Throwable) {
                continue
            }

            map[key] = node.value
        }

        return map.takeIf { it.isNotEmpty() }
    }

    private fun readSetValue(
        propertyReference: SetReference<Any, IsPropertyContext>?
    ): Set<Any>? {
        if (propertyReference == null) return null
        val setDefinition = propertyReference.propertyDefinition.definition
        val setPrefix = propertyReference.toStorageByteArray()
        val set = linkedSetOf<Any>()

        for (index in values.indices) {
            val node = getValueAtIndex<Any>(values, index) ?: continue
            val reference = node.reference
            if (reference.size <= setPrefix.size) continue
            if (!reference.copyOfRange(0, setPrefix.size).contentEquals(setPrefix)) continue

            val setItem = try {
                var readIndex = setPrefix.size
                val setItemLength = initIntByVar { reference[readIndex++] }
                @Suppress("UNCHECKED_CAST")
                val valueDefinition = setDefinition.valueDefinition as maryk.core.properties.definitions.IsStorageBytesEncodable<Any>
                val itemValue = valueDefinition.readStorageBytes(setItemLength) { reference[readIndex++] }
                if (readIndex != reference.size) continue
                itemValue
            } catch (_: Throwable) {
                continue
            }

            set += setItem
        }

        return set.takeIf { it.isNotEmpty() }
    }

    fun isDeleted(toVersion: HLC?): Boolean =
        getValue<Boolean>(this.values, objectSoftDeleteQualifier, toVersion)?.value == true

    fun <T : Any> matchQualifier(
        reference: IsPropertyReference<T, *, *>,
        toVersion: HLC?,
        recordFetcher: (IsRootDataModel, Key<*>) -> DataRecord<*>?,
        matcher: (T?) -> Boolean
    ) = this.matchQualifier(reference.toQualifierMatcher(), toVersion, recordFetcher, matcher)

    private fun <T : Any> matchQualifier(
        qualifierMatcher: IsQualifierMatcher,
        toVersion: HLC?,
        recordFetcher: (IsRootDataModel, Key<*>) -> DataRecord<*>?,
        matcher: (T?) -> Boolean
    ): Boolean {
        when (qualifierMatcher) {
            is QualifierExactMatcher -> {
                val value = get<T>(qualifierMatcher.reference, qualifierMatcher.qualifier, toVersion)
                    ?: return matcher(null)

                return when (val referencedMatcher = qualifierMatcher.referencedQualifierMatcher) {
                    null -> matcher(value)
                    else -> {
                        recordFetcher(
                            referencedMatcher.reference.propertyDefinition.dataModel,
                            value as Key<*>
                        )?.matchQualifier(
                            referencedMatcher.qualifierMatcher,
                            toVersion,
                            recordFetcher,
                            matcher
                        ) == true
                    }
                }
            }
            is QualifierFuzzyMatcher -> {
                val start = qualifierMatcher.firstPossible()
                val valueIndex = values.binarySearch {
                    it.reference compareTo start
                }.let {
                    if (it < 0) {
                        it * -1 - 1
                    } else it
                }

                qualifiers@for (index in (valueIndex..values.lastIndex)) {
                    val value = getValueAtIndex<T>(this.values, index, toVersion)
                        ?: continue@qualifiers

                    when (qualifierMatcher.isMatch(value.reference, 0)) {
                        NO_MATCH -> continue@qualifiers
                        MATCH -> {
                            val matches = when (val referencedMatcher = qualifierMatcher.referencedQualifierMatcher) {
                                null -> {
                                    val decoded = qualifierMatcher.reference?.let {
                                        readFuzzyValue(it, value.reference)
                                    }
                                    @Suppress("UNCHECKED_CAST")
                                    matcher((decoded ?: value.value) as T?)
                                }
                                else -> {
                                    recordFetcher(
                                        referencedMatcher.reference.comparablePropertyDefinition.dataModel,
                                        value.value as Key<*>
                                    )?.matchQualifier(
                                        referencedMatcher.qualifierMatcher, toVersion, recordFetcher, matcher
                                    ) == true
                                }
                            }

                            return if (matches) true else continue@qualifiers
                        }
                        OUT_OF_RANGE -> return false
                    }
                }
                return false
            }
        }
    }

    private fun readFuzzyValue(
        reference: IsPropertyReference<*, *, *>,
        qualifierBytes: ByteArray
    ): Any? {
        return when (reference) {
            is MapAnyKeyReference<*, *, *> -> {
                val parentLength = reference.toQualifierStorageByteArray()?.size ?: 0
                var readIndex = parentLength
                if (readIndex >= qualifierBytes.size) return null

                val keyLength = initIntByVar { qualifierBytes[readIndex++] }
                if (readIndex + keyLength > qualifierBytes.size) return null

                @Suppress("UNCHECKED_CAST")
                (reference as MapAnyKeyReference<Any, Any, *>).readStorageBytes(keyLength) { qualifierBytes[readIndex++] }
            }
            is SetAnyValueReference<*, *> -> {
                val parentLength = reference.toQualifierStorageByteArray()?.size ?: 0
                var readIndex = parentLength
                if (readIndex >= qualifierBytes.size) return null

                val valueLength = initIntByVar { qualifierBytes[readIndex++] }
                if (readIndex + valueLength > qualifierBytes.size) return null

                @Suppress("UNCHECKED_CAST")
                (reference as SetAnyValueReference<Any, *>).readStorageBytes(valueLength) { qualifierBytes[readIndex++] }
            }
            else -> null
        }
    }

    /** Get value by [reference] */
    operator fun <T : Any> get(reference: IsPropertyReference<T, *, *>, toVersion: HLC? = null): T? =
        get(reference, reference.toStorageByteArray(), toVersion)

    /** Get value by [reference] */
    operator fun <T : Any> get(orgReference: AnyPropertyReference?, reference: ByteArray, toVersion: HLC? = null): T? =
        getValue<Any>(this.values, reference, toVersion)?.value?.let {
            @Suppress("UNCHECKED_CAST")
            if (orgReference is SimpleTypedValueReference<*, *, *>) {
                val typedValue = it as TypedValue<*, *>
                if (it.type == orgReference.type) {
                    typedValue.value as T
                } else {
                    null
                }
            } else {
                it as T
            }
        }
}

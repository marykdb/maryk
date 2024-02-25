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
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SimpleTypedValueReference
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
    ): T? =
        getValue<T>(this.values, propertyReference.toStorageByteArray())?.value

    fun isDeleted(toVersion: HLC?): Boolean =
        getValue<Boolean>(this.values, objectSoftDeleteQualifier, toVersion)?.value ?: false

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
                        ) ?: false
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
                                null -> matcher(value.value)
                                else -> {
                                    recordFetcher(referencedMatcher.reference.comparablePropertyDefinition.dataModel, value.value as Key<*>)?.
                                        matchQualifier(referencedMatcher.qualifierMatcher, toVersion, recordFetcher, matcher)
                                        ?: false
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

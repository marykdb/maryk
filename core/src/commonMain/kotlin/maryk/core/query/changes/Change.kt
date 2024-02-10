package maryk.core.query.changes

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.ReferenceValuePairsDataModel
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues

/** Defines changes to properties defined by [referenceValuePairs] */
data class Change internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsChange {
    override val changeType = ChangeType.Change

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>?) : this(referenceValuePair.filterNotNull() as List<ReferenceValuePair<Any>>)

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootDataModel>): Change? {
        val filtered = referenceValuePairs.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else Change(filtered)
    }

    override fun validate(addException: (e: ValidationException) -> Unit) {
        for ((reference, value) in referenceValuePairs) {
            try {
                reference.comparablePropertyDefinition.validateWithRef(null, value, { reference })
            } catch (e: ValidationException) {
                addException(e)
            }
        }
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        val mutableReferenceList = mutableListOf<AnyPropertyReference>()

        for (referenceValuePair in referenceValuePairs) {
            referenceValuePair.reference.unwrap(mutableReferenceList)
            var referenceIndex = 0

            fun valueChanger(originalValue: Any?, newValue: Any?): Any? {
                val currentRef = mutableReferenceList.getOrNull(referenceIndex++)

                return if (currentRef == null) {
                    referenceValuePair.value
                } else {
                    deepValueChanger(
                        originalValue,
                        newValue,
                        currentRef,
                        ::valueChanger
                    )
                    null // Deeper change so no overwrite
                }
            }

            when (val ref = mutableReferenceList[referenceIndex++]) {
                is IsPropertyReferenceForValues<*, *, *, *> -> objectChanger(ref, ::valueChanger)
                else -> throw RequestException("Unsupported reference type: $ref")
            }
        }
    }

    override fun toString() = "Change[${referenceValuePairs.joinToString()}]"

    companion object : ReferenceValuePairsDataModel<Change, Companion, ReferenceValuePair<Any>, Any, Any>(
        pairGetter = Change::referenceValuePairs,
        pairModel = ReferenceValuePair,
    ) {
        override fun invoke(values: ObjectValues<Change, Companion>) = Change(
            referenceValuePairs = values(1u)
        )
    }
}

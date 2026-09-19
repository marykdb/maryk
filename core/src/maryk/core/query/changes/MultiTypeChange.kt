package maryk.core.query.changes

import maryk.core.models.IsRootDataModel
import maryk.core.models.ReferenceValuePairsDataModel
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.query.pairs.ReferenceTypePair
import maryk.core.values.ObjectValues

/** Defines a change in type for complex multi type value */
data class MultiTypeChange internal constructor(
    val referenceTypePairs: List<ReferenceTypePair<*>>
) : IsChange {
    override val changeType = ChangeType.TypeChange

    constructor(vararg referenceTypePair: ReferenceTypePair<*>) : this(referenceTypePair.toList())

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootDataModel>): MultiTypeChange? {
        val filtered = referenceTypePairs.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else MultiTypeChange(filtered)
    }

    override fun validate(addException: (e: ValidationException) -> Unit) {
        // No need to validate
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        // Ignore since it only describes change in type which can also be determined with actual value changes
    }

    companion object : ReferenceValuePairsDataModel<MultiTypeChange, Companion, ReferenceTypePair<*>, IndexedEnum, IndexedEnum>(
        pairGetter = MultiTypeChange::referenceTypePairs,
        pairModel = ReferenceTypePair,
    ) {
        override fun invoke(values: ObjectValues<MultiTypeChange, Companion>) = MultiTypeChange(
            referenceTypePairs = values(1u)
        )
    }
}

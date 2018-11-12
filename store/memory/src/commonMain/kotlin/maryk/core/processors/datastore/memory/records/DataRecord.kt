@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory.records

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.memory.records.DeleteState.NeverDeleted
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValuePropertyReference
import maryk.core.properties.types.Key

internal data class DataRecord<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val key: Key<DM>,
    val values: DataRecordValueTree<DM, P>,
    val firstVersion: ULong,
    val lastVersion: ULong,
    val isDeleted: DeleteState = NeverDeleted
) {
    operator fun <T: Any> get(reference: IsPropertyReference<T, *, *>): T {
        val references = reference.unwrap()

        var value: DataRecordNode? = null

        for (ref in references) {
            when (ref) {
                is ValuePropertyReference<*, *, *, *> -> {
                    val tree = when (value) {
                        null -> this.values
                        is DataRecordValueTreeNode<*, *> -> value.value
                        else -> throw Exception("ValuePropertyReference can only be used on DataRecordValueTreeNode")
                    }

                    val index = tree.recordNodes.binarySearch {
                        it.index.compareTo(ref.propertyDefinition.index.toUShort())
                    }

                    value = tree.recordNodes[index]
                }
                else -> throw Exception("WRONG")
            }
        }

        return if (value is DataRecordValue<*>) {
            @Suppress("UNCHECKED_CAST")
            value.value as T
        } else throw Exception("Unexpected Value $value from $reference")
    }
}

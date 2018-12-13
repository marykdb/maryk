@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.records

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.datastore.memory.processors.changers.getValue
import maryk.datastore.memory.records.DeleteState.NeverDeleted

/**
 * A DataRecord stored at [key] with [values]
 * [firstVersion] and [lastVersion] signify the versions of first and last change
 * [isDeleted] is a state switch to signify record was deleted
 */
internal data class DataRecord<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val key: Key<DM>,
    var values: List<DataRecordNode>,
    val firstVersion: ULong,
    var lastVersion: ULong,
    var isDeleted: DeleteState = NeverDeleted
) {
    /** Get value by [reference] */
    operator fun <T : Any> get(reference: IsPropertyReference<T, *, *>): T? =
        get(reference.toStorageByteArray())

    /** Get value by [reference] */
    operator fun <T : Any> get(reference: ByteArray): T? =
        getValue<T>(this.values, reference)?.value
}

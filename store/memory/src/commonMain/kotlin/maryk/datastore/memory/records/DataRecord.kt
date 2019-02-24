package maryk.datastore.memory.records

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.values.IsValuesGetter
import maryk.datastore.memory.processors.changers.getValue
import maryk.datastore.memory.processors.objectSoftDeleteQualifier

/**
 * A DataRecord stored at [key] with [values]
 * [firstVersion] and [lastVersion] signify the versions of first and last change
 * [isDeleted] is a state switch to signify record was deleted
 */
internal data class DataRecord<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions>(
    val key: Key<DM>,
    var values: List<DataRecordNode>,
    val firstVersion: ULong,
    var lastVersion: ULong
) : IsValuesGetter {
    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>) =
        getValue<T>(this.values, propertyReference.toStorageByteArray())?.value

    fun isDeleted(toVersion: ULong?): Boolean =
        getValue<Boolean>(this.values, objectSoftDeleteQualifier, toVersion)?.value ?: false

    /** Get value by [reference] */
    operator fun <T : Any> get(reference: IsPropertyReference<T, *, *>, toVersion: ULong? = null): T? =
        get(reference.toStorageByteArray(), toVersion)

    /** Get value by [reference] */
    operator fun <T : Any> get(reference: ByteArray, toVersion: ULong? = null): T? =
        getValue<T>(this.values, reference, toVersion)?.value
}

package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryModel
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext
import maryk.core.query.changes.ChangeType.IncMapChange
import maryk.core.values.ObjectValues

/**
 * Creates a DataObjectChange which contains [change] until [lastVersion] for a specific DataObject
 */
fun <DM : IsRootDataModel> Key<DM>.change(
    vararg change: IsChange,
    lastVersion: ULong? = null
) = DataObjectChange(this, change.toList(), lastVersion)

/**
 * Contains [changes] until [lastVersion] for a specific DataObject by [key]
 */
data class DataObjectChange<out DM : IsRootDataModel> internal constructor(
    val key: Key<DM>,
    val changes: List<IsChange>,
    val lastVersion: ULong? = null
) {
    fun validate() {
        createValidationUmbrellaException { addException ->
            changes.forEach {
                it.validate(addException)
            }
        }
    }

    companion object : QueryModel<DataObjectChange<*>, Companion>() {
        val key by contextual(
            index = 1u,
            getter = DataObjectChange<*>::key,
            definition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? IsRootDataModel ?: throw ContextNotFoundException()
                }
            )
        )

        val changes by list(
            index = 2u,
            getter = DataObjectChange<*>::changes,
            default = emptyList(),
            valueDefinition = ContextCaptureDefinition(
                InternalMultiTypeDefinition(
                    typeEnum = ChangeType,
                    definitionMap = mapOfChangeDefinitions
                ),
                capturer = { context, value ->
                    if (value.type == IncMapChange) {
                        context?.collectIncMapChange(value.value as maryk.core.query.changes.IncMapChange)
                    }
                }
            ),
            toSerializable = { TypedValue(it.changeType, it) },
            fromSerializable = { it.value }
        )

        val lastVersion by number(3u, DataObjectChange<*>::lastVersion, type = UInt64)

        override fun invoke(values: ObjectValues<DataObjectChange<*>, Companion>) = DataObjectChange<IsRootDataModel>(
            key = values(key.index),
            changes = values(changes.index),
            lastVersion = values(lastVersion.index)
        )
    }
}

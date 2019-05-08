package maryk.core.query.responses

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsResponseStatus
import maryk.core.query.responses.statuses.RequestFail
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.StatusType
import maryk.core.query.responses.statuses.StatusType.ADD_SUCCESS
import maryk.core.query.responses.statuses.StatusType.ALREADY_EXISTS
import maryk.core.query.responses.statuses.StatusType.AUTH_FAIL
import maryk.core.query.responses.statuses.StatusType.DOES_NOT_EXIST
import maryk.core.query.responses.statuses.StatusType.REQUEST_FAIL
import maryk.core.query.responses.statuses.StatusType.SERVER_FAIL
import maryk.core.query.responses.statuses.StatusType.SUCCESS
import maryk.core.query.responses.statuses.StatusType.VALIDATION_FAIL
import maryk.core.query.responses.statuses.Success
import maryk.core.query.responses.statuses.ValidationFail

/** A response for a data operation on a DataModel */
interface IsDataModelResponse<out DM : IsRootDataModel<*>> : IsResponse {
    val dataModel: DM

    companion object {
        internal fun <DM : Any> addDataModel(
            definitions: ObjectPropertyDefinitions<DM>,
            getter: (DM) -> IsRootDataModel<*>?
        ) {
            definitions.add(1u, "dataModel",
                ContextualModelReferenceDefinition<IsRootDataModel<*>, RequestContext>(
                    contextualResolver = { context, name ->
                        context?.let {
                            @Suppress("UNCHECKED_CAST")
                            it.dataModels[name] as (Unit.() -> IsRootDataModel<*>)?
                                ?: throw DefNotFoundException("ObjectDataModel of name $name not found on dataModels")
                        } ?: throw ContextNotFoundException()
                    }
                ),
                getter = getter,
                toSerializable = { value: IsRootDataModel<*>?, _ ->
                    value?.let {
                        DataModelReference(it.name) { it }
                    }
                },
                fromSerializable = { it?.get?.invoke(Unit) },
                capturer = { context, value ->
                    context.dataModel = value.get(Unit)
                }
            )
        }

        internal fun <DM : Any> addStatuses(
            definitions: ObjectPropertyDefinitions<DM>,
            getter: (DM) -> List<TypedValue<StatusType, IsResponseStatus>>?
        ) {
            definitions.add(2u, "statuses", listOfStatuses, getter)
        }
    }
}

private val listOfStatuses = ListDefinition(
    valueDefinition = MultiTypeDefinition(
        typeEnum = StatusType,
        definitionMap = mapOf(
            SUCCESS to EmbeddedObjectDefinition(dataModel = { Success }),
            ADD_SUCCESS to EmbeddedObjectDefinition(dataModel = { AddSuccess }),
            AUTH_FAIL to EmbeddedObjectDefinition(dataModel = { AuthFail }),
            REQUEST_FAIL to EmbeddedObjectDefinition(dataModel = { RequestFail }),
            SERVER_FAIL to EmbeddedObjectDefinition(dataModel = { ServerFail }),
            VALIDATION_FAIL to EmbeddedObjectDefinition(dataModel = { ValidationFail }),
            ALREADY_EXISTS to EmbeddedObjectDefinition(dataModel = { AlreadyExists }),
            DOES_NOT_EXIST to EmbeddedObjectDefinition(dataModel = { DoesNotExist })
        )
    )
)

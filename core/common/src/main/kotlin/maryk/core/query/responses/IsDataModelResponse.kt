package maryk.core.query.responses

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.RequestFail
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.StatusType
import maryk.core.query.responses.statuses.Success
import maryk.core.query.responses.statuses.ValidationFail

/** A response for a data operation on a DataModel */
interface IsDataModelResponse<DO: Any, out DM: RootDataModel<DO, *>>{
    val dataModel: DM

    companion object {
        internal fun <DM: Any> addDataModel(definitions: PropertyDefinitions<DM>, getter: (DM) -> RootDataModel<*, *>?) {
            definitions.add(0, "dataModel",
                ContextualModelReferenceDefinition<RootDataModel<*, *>, DataModelPropertyContext>(
                    contextualResolver = { context, name ->
                        context?.let {
                            it.dataModels[name]?.invoke() as RootDataModel<*, *>? ?: throw DefNotFoundException("DataModel of name $name not found on dataModels")
                        } ?: throw ContextNotFoundException()
                    }
                ),
                getter = getter,
                toSerializable = { it: RootDataModel<*, *>? ->
                    it?.let{
                        DataModelReference(it.name){ it }
                    }
                },
                fromSerializable = { it?.get?.invoke() },
                capturer = { context, value ->
                    @Suppress("UNCHECKED_CAST")
                    context.dataModel = value.get() as RootDataModel<Any, PropertyDefinitions<Any>>
                }
            )
        }
        internal fun <DM: Any> addStatuses(definitions: PropertyDefinitions<DM>, getter: (DM) -> List<TypedValue<StatusType, *>>?){
            definitions.add(1, "statuses", listOfStatuses, getter)
        }
    }
}

private val listOfStatuses = ListDefinition(
    valueDefinition = MultiTypeDefinition(
        typeEnum = StatusType,
        definitionMap = mapOf(
            StatusType.SUCCESS to SubModelDefinition(dataModel = {  Success } ),
            StatusType.ADD_SUCCESS to SubModelDefinition(dataModel = {  AddSuccess } ),
            StatusType.AUTH_FAIL to SubModelDefinition(dataModel = {  AuthFail } ),
            StatusType.REQUEST_FAIL to SubModelDefinition(dataModel = {  RequestFail } ),
            StatusType.SERVER_FAIL to SubModelDefinition(dataModel = {  ServerFail } ),
            StatusType.VALIDATION_FAIL to SubModelDefinition(dataModel = {  ValidationFail } ),
            StatusType.ALREADY_EXISTS to SubModelDefinition(dataModel = {  AlreadyExists } ),
            StatusType.DOES_NOT_EXIST to SubModelDefinition(dataModel = {  DoesNotExist } )
        )
    )
)

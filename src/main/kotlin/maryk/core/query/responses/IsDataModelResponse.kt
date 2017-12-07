package maryk.core.query.responses

import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
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
interface IsDataModelResponse<DO: Any, out DM: RootDataModel<DO>>{
    val dataModel: DM

    companion object {
        internal fun <DM: Any> addDataModel(definitions: PropertyDefinitions<DM>, getter: (DM) -> RootDataModel<*>?) {
            definitions.add(0, "dataModel", dataModel, getter)
        }
        internal fun <DM: Any> addStatuses(definitions: PropertyDefinitions<DM>, getter: (DM) -> List<TypedValue<*>>?){
            definitions.add(1, "statuses", listOfStatuses, getter)
        }
    }
}

private val dataModel = ContextCaptureDefinition(
        ContextualModelReferenceDefinition<DataModelPropertyContext>(
                contextualResolver = { context, name ->
                    context!!.dataModels[name]!!
                }
        )
){ context, value ->
    @Suppress("UNCHECKED_CAST")
    context!!.dataModel = value as RootDataModel<Any>
}

private val listOfStatuses = ListDefinition(
        valueDefinition = MultiTypeDefinition(
                getDefinition = mapOf(
                        StatusType.SUCCESS.index to SubModelDefinition(dataModel = {  Success } ),
                        StatusType.ADD_SUCCESS.index to SubModelDefinition(dataModel = {  AddSuccess } ),
                        StatusType.AUTH_FAIL.index to SubModelDefinition(dataModel = {  AuthFail } ),
                        StatusType.REQUEST_FAIL.index to SubModelDefinition(dataModel = {  RequestFail } ),
                        StatusType.SERVER_FAIL.index to SubModelDefinition(dataModel = {  ServerFail } ),
                        StatusType.VALIDATION_FAIL.index to SubModelDefinition(dataModel = {  ValidationFail } ),
                        StatusType.ALREADY_EXISTS.index to SubModelDefinition(dataModel = {  AlreadyExists } ),
                        StatusType.DOES_NOT_EXIST.index to SubModelDefinition(dataModel = {  DoesNotExist } )
                )::get
        )
)
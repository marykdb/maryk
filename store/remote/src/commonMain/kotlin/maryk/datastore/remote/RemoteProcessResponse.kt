package maryk.datastore.remote

import maryk.core.models.IsObjectDataModel
import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.number
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.IsDataModelResponse
import maryk.core.values.ObjectValues

internal enum class ProcessResultType(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null,
    override val definition: IsUsableInMultiType<IsDataModelResponse<*>, *>?,
) : IndexedEnumComparable<ProcessResultType>, IsCoreEnum, MultiTypeEnum<IsDataModelResponse<*>> {
    Add(1u, definition = responseDefinition(EmbeddedObjectDefinition(dataModel = { AddResponse }))),
    Change(2u, definition = responseDefinition(EmbeddedObjectDefinition(dataModel = { ChangeResponse }))),
    Delete(3u, definition = responseDefinition(EmbeddedObjectDefinition(dataModel = { DeleteResponse }))),
    AddOrChange(4u, definition = responseDefinition(EmbeddedObjectDefinition(dataModel = { AddOrChangeResponse })));

    companion object : MultiTypeEnumDefinition<ProcessResultType>(ProcessResultType::class, { entries })
}

@Suppress("UNCHECKED_CAST")
private fun responseDefinition(
    definition: EmbeddedObjectDefinition<out IsDataModelResponse<*>, *, *, *>,
): IsUsableInMultiType<IsDataModelResponse<*>, *> =
    definition as IsUsableInMultiType<IsDataModelResponse<*>, *>

internal data class RemoteProcessResponse(
    val version: ULong,
    val result: IsDataModelResponse<*>,
) {
    companion object : TypedObjectDataModel<RemoteProcessResponse, IsObjectDataModel<RemoteProcessResponse>, RequestContext, RequestContext>() {
        val version by number(index = 1u, getter = RemoteProcessResponse::version, type = UInt64)
        val result by multiType(
            index = 2u,
            getter = RemoteProcessResponse::result,
            typeEnum = ProcessResultType,
            toSerializable = { value: IsDataModelResponse<*>?, _: RequestContext? ->
                value?.let { resultType(it)(it) }
            },
            fromSerializable = { typed: TypedValue<ProcessResultType, IsDataModelResponse<*>>? ->
                typed?.value
            },
        )

        override fun invoke(values: ObjectValues<RemoteProcessResponse, IsObjectDataModel<RemoteProcessResponse>>) =
            RemoteProcessResponse(
                version = values(version.index),
                result = values(result.index),
            )

        private fun resultType(result: IsDataModelResponse<*>): ProcessResultType = when (result) {
            is AddResponse<*> -> ProcessResultType.Add
            is ChangeResponse<*> -> ProcessResultType.Change
            is DeleteResponse<*> -> ProcessResultType.Delete
            is AddOrChangeResponse<*> -> ProcessResultType.AddOrChange
            else -> throw IllegalArgumentException("Unsupported process response type: ${result::class.simpleName}")
        }
    }
}

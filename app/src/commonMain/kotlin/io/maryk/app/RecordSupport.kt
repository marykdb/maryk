package io.maryk.app

import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.DataObjectChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.Values
import maryk.core.yaml.MarykYamlReader
import maryk.datastore.shared.IsDataStore
import maryk.json.JsonWriter
import maryk.yaml.YamlWriter
import maryk.core.properties.types.Key

internal data class ApplyResult(
    val message: String,
    val success: Boolean,
)

internal fun serializeValuesToYaml(
    dataModel: IsRootDataModel,
    values: Values<IsRootDataModel>,
    requestContext: RequestContext? = null,
): String {
    @Suppress("UNCHECKED_CAST")
    val serializer = dataModel.Serializer as IsDataModelSerializer<
        Values<IsRootDataModel>,
        IsRootDataModel,
        IsPropertyContext
    >
    return buildString {
        val writer = YamlWriter { append(it) }
        serializer.writeJson(values, writer, context = requestContext)
    }
}

internal fun serializeValuesToJson(
    dataModel: IsRootDataModel,
    values: Values<IsRootDataModel>,
    requestContext: RequestContext? = null,
): String {
    @Suppress("UNCHECKED_CAST")
    val serializer = dataModel.Serializer as IsDataModelSerializer<
        Values<IsRootDataModel>,
        IsRootDataModel,
        IsPropertyContext
    >
    return buildString {
        val writer = JsonWriter(pretty = true) { append(it) }
        serializer.writeJson(values, writer, context = requestContext)
    }
}

internal fun serializeRecordToYaml(
    dataModel: IsRootDataModel,
    valuesWithMetaData: ValuesWithMetaData<IsRootDataModel>,
): String {
    return serializeValuesToYaml(dataModel, valuesWithMetaData.values)
}

internal fun parseValuesFromYaml(
    dataModel: IsRootDataModel,
    yaml: String,
): Values<IsRootDataModel> {
    @Suppress("UNCHECKED_CAST")
    val serializer = dataModel.Serializer as IsDataModelSerializer<
        Values<IsRootDataModel>,
        IsRootDataModel,
        IsPropertyContext
    >
    return serializer.readJson(MarykYamlReader(yaml), null)
}

internal fun applyChanges(
    dataStore: IsDataStore,
    dataModel: IsRootDataModel,
    key: Key<IsRootDataModel>,
    changes: List<IsChange>,
    ifVersion: ULong? = null,
    action: String = "Updated",
    target: String? = null,
): ApplyResult {
    if (changes.isEmpty()) {
        return ApplyResult("No changes to apply.", success = false)
    }

    val objectChange: DataObjectChange<IsRootDataModel> = key.change(*changes.toTypedArray(), lastVersion = ifVersion)
    val request = dataModel.change(objectChange)
    val response: ChangeResponse<IsRootDataModel> = runBlocking { dataStore.execute(request) }
    return formatStatus(
        label = "${dataModel.Meta.name} $key",
        action = action,
        target = target,
        response = response,
    )
}

internal fun buildRequestContext(dataModel: IsRootDataModel): RequestContext {
    val definitionsContext = DefinitionsContext(
        mutableMapOf(dataModel.Meta.name to DataModelReference(dataModel))
    )
    return RequestContext(definitionsContext, dataModel = dataModel)
}

private fun formatStatus(
    label: String,
    action: String,
    target: String?,
    response: ChangeResponse<IsRootDataModel>,
): ApplyResult {
    val status = response.statuses.firstOrNull()
        ?: return ApplyResult("$action failed: no response status for $label.", success = false)
    val targetLabel = target?.let { " from $it" }.orEmpty()
    return when (status) {
        is ChangeSuccess -> ApplyResult(
            "$action $label$targetLabel (version ${status.version}).",
            success = true,
        )
        is DoesNotExist -> ApplyResult(
            "$action failed: $label does not exist.",
            success = false,
        )
        is ValidationFail -> {
            val details = status.exceptions.joinToString(separator = "; ") { exception ->
                exception.message ?: exception.toString()
            }
            ApplyResult("$action failed: $details", success = false)
        }
        is ServerFail -> ApplyResult("$action failed: ${status.reason}", success = false)
        else -> ApplyResult("$action failed: ${status.statusType}", success = false)
    }
}

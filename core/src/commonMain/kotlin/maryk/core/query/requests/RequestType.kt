package maryk.core.query.requests

import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.query.requests.RequestType.Add
import maryk.core.query.requests.RequestType.Change
import maryk.core.query.requests.RequestType.Collect
import maryk.core.query.requests.RequestType.Delete
import maryk.core.query.requests.RequestType.Get
import maryk.core.query.requests.RequestType.GetChanges
import maryk.core.query.requests.RequestType.GetUpdates
import maryk.core.query.requests.RequestType.Scan
import maryk.core.query.requests.RequestType.ScanChanges
import maryk.core.query.requests.RequestType.ScanUpdates
import kotlin.native.concurrent.SharedImmutable

enum class RequestType(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<RequestType>, TypeEnum<Any>, IsCoreEnum {
    Add(1u),
    Change(2u),
    Delete(3u),
    Get(4u),
    GetChanges(5u),
    GetUpdates(6u),
    Scan(7u),
    ScanChanges(8u),
    ScanUpdates(9u),
    Collect(10u);

    companion object : IndexedEnumDefinition<RequestType>(
        "RequestType", RequestType::values
    )
}

@SharedImmutable
val mapOfRequestTypeEmbeddedObjectDefinitions = mapOf(
    Add to EmbeddedObjectDefinition(dataModel = { AddRequest.Model }),
    Change to EmbeddedObjectDefinition(dataModel = { ChangeRequest.Model }),
    Delete to EmbeddedObjectDefinition(dataModel = { DeleteRequest.Model }),
    Get to EmbeddedObjectDefinition(dataModel = { GetRequest.Model }),
    GetChanges to EmbeddedObjectDefinition(dataModel = { GetChangesRequest.Model }),
    GetUpdates to EmbeddedObjectDefinition(dataModel = { GetUpdatesRequest.Model }),
    Scan to EmbeddedObjectDefinition(dataModel = { ScanRequest.Model }),
    ScanChanges to EmbeddedObjectDefinition(dataModel = { ScanChangesRequest.Model }),
    ScanUpdates to EmbeddedObjectDefinition(dataModel = { ScanUpdatesRequest.Model }),
    Collect to EmbeddedObjectDefinition(dataModel = { CollectRequest })
)

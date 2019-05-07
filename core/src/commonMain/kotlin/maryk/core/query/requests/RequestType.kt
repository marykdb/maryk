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
import maryk.core.query.requests.RequestType.Scan
import maryk.core.query.requests.RequestType.ScanChanges

enum class RequestType(override val index: UInt) : IndexedEnumComparable<RequestType>, TypeEnum<IsRequest<*>>, IsCoreEnum {
    Add(1u),
    Change(2u),
    Delete(3u),
    Get(4u),
    GetChanges(5u),
    Scan(6u),
    ScanChanges(7u),
    Collect(8u);

    companion object : IndexedEnumDefinition<RequestType>(
        "RequestType", RequestType::values
    )
}

val mapOfRequestTypeEmbeddedObjectDefinitions = mapOf(
    Add to EmbeddedObjectDefinition(dataModel = { AddRequest }),
    Change to EmbeddedObjectDefinition(dataModel = { ChangeRequest }),
    Delete to EmbeddedObjectDefinition(dataModel = { DeleteRequest }),
    Get to EmbeddedObjectDefinition(dataModel = { GetRequest }),
    GetChanges to EmbeddedObjectDefinition(dataModel = { GetChangesRequest }),
    Scan to EmbeddedObjectDefinition(dataModel = { ScanRequest }),
    ScanChanges to EmbeddedObjectDefinition(dataModel = { ScanChangesRequest }),
    Collect to EmbeddedObjectDefinition(dataModel = { CollectRequest })
)

package maryk.core.query.requests

import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

enum class RequestType(override val index: Int): IndexedEnum<RequestType> {
    Add(0),
    Change(1),
    Delete(2),
    Get(3),
    GetChanges(4),
    GetVersionedChanges(5),
    Scan(6),
    ScanChanges(7),
    ScanVersionedChanges(8);

    companion object: IndexedEnumDefinition<RequestType>(
        "RequestType", RequestType::values
    )
}


internal val mapOfRequestTypeEmbeddedObjectDefinitions = mapOf(
    RequestType.Add to EmbeddedObjectDefinition(dataModel = { AddRequest }),
    RequestType.Change to EmbeddedObjectDefinition(dataModel = { ChangeRequest }),
    RequestType.Delete to EmbeddedObjectDefinition(dataModel = { DeleteRequest }),
    RequestType.Get to EmbeddedObjectDefinition(dataModel = { GetRequest }),
    RequestType.GetChanges to EmbeddedObjectDefinition(dataModel = { GetChangesRequest }),
    RequestType.GetVersionedChanges to EmbeddedObjectDefinition(dataModel = { GetVersionedChangesRequest }),
    RequestType.Scan to EmbeddedObjectDefinition(dataModel = { ScanRequest }),
    RequestType.ScanChanges to EmbeddedObjectDefinition(dataModel = { ScanChangesRequest }),
    RequestType.ScanVersionedChanges to EmbeddedObjectDefinition(dataModel = { ScanVersionedChangesRequest })
)

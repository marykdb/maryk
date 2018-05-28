package maryk.core.query.requests

import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.IndexedEnumDefinition

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


internal val mapOfRequestTypeSubModelDefinitions = mapOf(
    RequestType.Add to SubModelDefinition(dataModel = { AddRequest }),
    RequestType.Change to SubModelDefinition(dataModel = { ChangeRequest }),
    RequestType.Delete to SubModelDefinition(dataModel = { DeleteRequest }),
    RequestType.Get to SubModelDefinition(dataModel = { GetRequest }),
    RequestType.GetChanges to SubModelDefinition(dataModel = { GetChangesRequest }),
    RequestType.GetVersionedChanges to SubModelDefinition(dataModel = { GetVersionedChangesRequest }),
    RequestType.Scan to SubModelDefinition(dataModel = { ScanRequest }),
    RequestType.ScanChanges to SubModelDefinition(dataModel = { ScanChangesRequest }),
    RequestType.ScanVersionedChanges to SubModelDefinition(dataModel = { ScanVersionedChangesRequest })
)

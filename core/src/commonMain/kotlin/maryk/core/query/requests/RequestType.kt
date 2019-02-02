package maryk.core.query.requests

import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

enum class RequestType(override val index: UInt): IndexedEnum<RequestType> {
    Add(1u),
    Change(2u),
    Delete(3u),
    Get(4u),
    GetChanges(5u),
    Scan(6u),
    ScanChanges(7u),
	Collect(8u);

    companion object: IndexedEnumDefinition<RequestType>(
        "RequestType", RequestType::values
    )
}

val mapOfRequestTypeEmbeddedObjectDefinitions = mapOf(
    RequestType.Add to EmbeddedObjectDefinition(dataModel = { AddRequest }),
    RequestType.Change to EmbeddedObjectDefinition(dataModel = { ChangeRequest }),
    RequestType.Delete to EmbeddedObjectDefinition(dataModel = { DeleteRequest }),
    RequestType.Get to EmbeddedObjectDefinition(dataModel = { GetRequest }),
    RequestType.GetChanges to EmbeddedObjectDefinition(dataModel = { GetChangesRequest }),
    RequestType.Scan to EmbeddedObjectDefinition(dataModel = { ScanRequest }),
    RequestType.ScanChanges to EmbeddedObjectDefinition(dataModel = { ScanChangesRequest }),
    RequestType.Collect to EmbeddedObjectDefinition(dataModel = { CollectRequest })
)

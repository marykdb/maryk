package maryk.datastore.remote

import maryk.core.query.requests.TransactionCapabilities
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.definitions.Definitions
import maryk.core.models.DefinitionModel
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.ObjectValues

internal data class RemoteStoreModelId(
    val id: UInt,
    val name: String,
) {
    companion object : DefinitionModel<RemoteStoreModelId>() {
        val id by number(index = 1u, getter = RemoteStoreModelId::id, type = UInt32)
        val name by string(index = 2u, getter = RemoteStoreModelId::name)

        override fun invoke(values: ObjectValues<RemoteStoreModelId, IsObjectDataModel<RemoteStoreModelId>>) = RemoteStoreModelId(
            id = values(id.index),
            name = values(name.index),
        )
    }
}

internal data class RemoteStoreInfo(
    val definitions: Definitions,
    val modelIds: List<RemoteStoreModelId>,
    val keepAllVersions: Boolean,
    val supportsFuzzyQualifierFiltering: Boolean,
    val supportsSubReferenceFiltering: Boolean,
    val keepUpdateHistoryIndex: Boolean = false,
    val supportsSnapshotVersion: Boolean = false,
    val supportsAtomicRequests: Boolean = false,
    val atomicCapabilities: AtomicCapabilities? = null,
    val transactionCapabilities: TransactionCapabilities? = null,
    val valueStorageFormat: UInt = 1u,
) {
    init {
        require(!supportsAtomicRequests || atomicCapabilities != null) { "Atomic support requires capability metadata" }
    }

    companion object : DefinitionModel<RemoteStoreInfo>() {
        val definitions by embedObject(
            index = 1u,
            getter = RemoteStoreInfo::definitions,
            dataModel = { Definitions },
        )
        val modelIds by list(
            index = 2u,
            getter = RemoteStoreInfo::modelIds,
            valueDefinition = EmbeddedObjectDefinition(dataModel = { RemoteStoreModelId }),
        )
        val keepAllVersions by boolean(index = 3u, getter = RemoteStoreInfo::keepAllVersions)
        val supportsFuzzyQualifierFiltering by boolean(
            index = 4u,
            getter = RemoteStoreInfo::supportsFuzzyQualifierFiltering,
        )
        val supportsSubReferenceFiltering by boolean(
            index = 5u,
            getter = RemoteStoreInfo::supportsSubReferenceFiltering,
        )
        val keepUpdateHistoryIndex by boolean(
            index = 6u,
            getter = RemoteStoreInfo::keepUpdateHistoryIndex,
            default = false
        )
        val supportsSnapshotVersion by boolean(
            index = 7u,
            getter = RemoteStoreInfo::supportsSnapshotVersion,
            default = false,
        )

        val supportsAtomicRequests by boolean(
            8u, RemoteStoreInfo::supportsAtomicRequests, default = false,
            toSerializable = { value: Boolean?, _: ContainsDefinitionsContext? -> value?.takeIf { it } },
        )

        val atomicCapabilities by embedObject(9u, RemoteStoreInfo::atomicCapabilities, dataModel = { AtomicCapabilities }, required = false)

        val transactionCapabilities by embedObject(10u, RemoteStoreInfo::transactionCapabilities, dataModel = { TransactionCapabilities }, required = false)

        val valueStorageFormat by number(
            12u, RemoteStoreInfo::valueStorageFormat, type = UInt32, default = 1u,
            toSerializable = { value: UInt?, _: ContainsDefinitionsContext? -> value?.takeIf { it != 1u } },
        )

        override fun invoke(values: ObjectValues<RemoteStoreInfo, IsObjectDataModel<RemoteStoreInfo>>) = RemoteStoreInfo(
            definitions = values(definitions.index),
            modelIds = values(modelIds.index),
            keepAllVersions = values(keepAllVersions.index),
            supportsFuzzyQualifierFiltering = values(supportsFuzzyQualifierFiltering.index),
            supportsSubReferenceFiltering = values(supportsSubReferenceFiltering.index),
            keepUpdateHistoryIndex = values(keepUpdateHistoryIndex.index),
            supportsSnapshotVersion = values(supportsSnapshotVersion.index),
            supportsAtomicRequests = values(supportsAtomicRequests.index),
            atomicCapabilities = values(atomicCapabilities.index),
            transactionCapabilities = values(transactionCapabilities.index),
            valueStorageFormat = values(valueStorageFormat.index),
        )
    }
}

package maryk.datastore.remote

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
) {
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

        override fun invoke(values: ObjectValues<RemoteStoreInfo, IsObjectDataModel<RemoteStoreInfo>>) = RemoteStoreInfo(
            definitions = values(definitions.index),
            modelIds = values(modelIds.index),
            keepAllVersions = values(keepAllVersions.index),
            supportsFuzzyQualifierFiltering = values(supportsFuzzyQualifierFiltering.index),
            supportsSubReferenceFiltering = values(supportsSubReferenceFiltering.index),
        )
    }
}

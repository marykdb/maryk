package maryk.core.models.definitions

import maryk.core.definitions.MarykPrimitiveDescriptor
import maryk.core.definitions.PrimitiveType

/**
 * A Definition for an [maryk.core.models.ObjectDataModel].
 * Mostly used for internal models which never need to be transported.
 */
data class ObjectDataModelDefinition(
    override val name: String,
) : IsDataModelDefinition, MarykPrimitiveDescriptor {
    override val primitiveType: PrimitiveType = PrimitiveType.ObjectModel
}

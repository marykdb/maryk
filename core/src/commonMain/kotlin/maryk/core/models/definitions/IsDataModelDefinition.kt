package maryk.core.models.definitions

import maryk.core.definitions.MarykPrimitiveDescriptor

/**
 * A definition for metadata of any DataModel.
 * Describes metadata like name, the type of model it represents,
 * and in extensions anything that needs to describe how data should be stored/indexed etc.
 */
interface IsDataModelDefinition: MarykPrimitiveDescriptor

package maryk.core.models.definitions

import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.types.Version

/**
 * A definition of a data model which defines object which can be stored as root object.
 *
 * In addition to the properties inherited from [IsValuesDataModelDefinition], this interface defines properties
 * to describe the key used to identify each record using this data model.
 *
 * The interface also defines a [version] property, which can be used to support migration strategies between
 * different versions of the data model.
 *
 * Finally, the [indices] property enables you to define multiple indices for the data model, so that
 * data can be retrieved efficiently from an index which fits a query.
 */
interface IsRootDataModelDefinition : IsValuesDataModelDefinition {
    /** Defines the shape of the key used to identify each record. */
    val keyDefinition: IsIndexable

    /** Specifies the size of the key in bytes. */
    val keyByteSize: Int

    /** Specifies the indices within the key that correspond to each component of the key definition. */
    val keyIndices: IntArray

    /** Specifies the version of the data model. */
    val version: Version

    /** Specifies additional indices for the data model for easier retrieval. */
    val indices: List<IsIndexable>?

    /**
     * The amount of bytes which have to be equal in start and end of scan range.
     * This is to protect the developer on doing non-optimal expensive scans.
     *
     * Default is key length
     */
    val minimumKeyScanByteRange: UInt?
}

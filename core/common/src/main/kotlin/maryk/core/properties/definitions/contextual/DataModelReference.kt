package maryk.core.properties.definitions.contextual

import maryk.core.models.ObjectDataModel

interface IsDataModelReference<DM: ObjectDataModel<*, *>> {
    val name: String
    val get: () -> DM
}

/** Reference to a ObjectDataModel */
class DataModelReference<DM: ObjectDataModel<*, *>>(
    override val name: String,
    override val get: () -> DM
): IsDataModelReference<DM> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IsDataModelReference<*>) return false

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

/** Lazy reference to a ObjectDataModel */
class LazyDataModelReference<DM: ObjectDataModel<*, *>>(
    override val name: String,
    getLater: () -> () -> DM
): IsDataModelReference<DM> {
    private val internal = lazy {
        getLater()
    }

    override val get = {
        internal.value()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IsDataModelReference<*>) return false

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

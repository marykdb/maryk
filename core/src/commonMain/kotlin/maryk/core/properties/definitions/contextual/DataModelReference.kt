package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyDefinitions

interface IsDataModelReference<DM : IsPropertyDefinitions> {
    val name: String
    val get: Unit.() -> DM
}

/** Reference to a ObjectDataModel */
class DataModelReference<DM : IsPropertyDefinitions>(
    override val name: String,
    override val get: Unit.() -> DM
) : IsDataModelReference<DM> {
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
class LazyDataModelReference<DM : IsPropertyDefinitions>(
    override val name: String,
    getLater: () -> Unit.() -> DM
) : IsDataModelReference<DM> {
    private val internal = lazy {
        getLater()
    }

    override val get: Unit.() -> DM = {
        internal.value(Unit)
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

package maryk.core.properties.definitions.contextual

import maryk.core.models.IsDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsStorableDataModel

interface IsDataModelReference<DM : IsDataModel> {
    val name: String
    val keyLength: Int?
    val get: () -> DM
}

/** Reference to a ObjectDataModel */
class DataModelReference<DM : IsDataModel>(
    override val name: String,
    override val keyLength: Int? = null,
    override val get: () -> DM
) : IsDataModelReference<DM> {

    constructor(dataModel: DM) : this(
        name = (dataModel as? IsStorableDataModel<*>)?.Meta?.name ?: dataModel::class.simpleName!!,
        keyLength = (dataModel as? IsRootDataModel)?.Meta?.keyByteSize,
        get = { dataModel }
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IsDataModelReference<*>) return false

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString() = "DataModelReference($name)"
}

/** Lazy reference to a ObjectDataModel */
class LazyDataModelReference<DM : IsDataModel>(
    override val name: String,
    override val keyLength: Int?,
    getLater: () -> () -> DM,
) : IsDataModelReference<DM> {
    private val internal = lazy(getLater)

    override val get: () -> DM = {
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

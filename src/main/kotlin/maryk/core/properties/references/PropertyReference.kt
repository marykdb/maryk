package maryk.core.properties.references

import maryk.core.objects.DataModel
import maryk.core.properties.definitions.IsPropertyDefinition

/**
 * Reference to a property
 * @param <T> Type of reference
 * @param <D> Definition of property
 */
open class PropertyReference<T: Any, out D : IsPropertyDefinition<T>> (
        val propertyDefinition: D,
        val parentReference: PropertyReference<*, *>?,
        val indexable: Boolean = true,
        val dataModel: DataModel<*>? = parentReference?.parentDataModel
) {
    open val name = propertyDefinition.name

    /** The name of property which is referenced */
    open val completeName: String? get() = this.parentReference?.let {
        if(name != null) {
            "${it.completeName}.$name"
        } else {
            it.completeName
        }
    } ?: name

    /** the parent DataObject definition */
    open val parentDataModel: DataModel<*>? by lazy { this.parentReference?.parentDataModel }

    override fun equals(other: Any?) = when {
        this === other -> true
        other == null || other !is PropertyReference<*, *> -> false
        else -> other.completeName!!.contentEquals(this.completeName!!)
    }

    override fun hashCode() = this.completeName?.hashCode() ?: 0
}

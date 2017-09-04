package maryk.core.properties.references

import maryk.core.objects.DataModel
import maryk.core.properties.definitions.IsPropertyDefinition

/**
 * Interface for properties which can have complex children
 * @param <T> Type contained within Property
 * @param <D> Type of property definition
 */
open class CanHaveSimpleChildReference<T: Any, out D : IsPropertyDefinition<T>>(
        definition: D,
        parentReference: PropertyReference<*, *>? = null,
        indexable: Boolean = true,
        dataModel: DataModel<*>? = parentReference?.parentDataModel
) : PropertyReference<T, D>(definition, parentReference, indexable, dataModel)

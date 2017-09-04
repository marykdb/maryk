package maryk.core.properties.references

import maryk.core.objects.DataModel
import maryk.core.properties.definitions.IsPropertyDefinition

/**
 * Interface for properties which can have children
 * @param name            set property name
 * @param def             definition of property
 * @param parentReference parent to reference to
 * @param <T> Type contained within Property
 * @param <D> Type of property definition
 */
open class CanHaveComplexChildReference<T: Any, out D : IsPropertyDefinition<T>>(
        definition: D,
        parentReference: PropertyReference<*, *>? = null,
        indexable: Boolean = true,
        dataModel: DataModel<*>? = parentReference?.parentDataModel
) : CanHaveSimpleChildReference<T, D>(definition, parentReference, indexable, dataModel)
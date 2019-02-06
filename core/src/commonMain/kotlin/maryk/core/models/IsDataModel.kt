package maryk.core.models

import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.IsValues

/** A DataModel which holds properties and can be validated */
interface IsDataModel<P: IsPropertyDefinitions> {
    /** Object which contains all property definitions. Can also be used to get property references. */
    val properties: P

    /**
     * Get property reference fetcher of this DataModel with [referenceGetter]
     * Optionally pass an already resolved [parent]
     * For Strongly typed reference notation
     */
    operator fun <T: Any, W: IsPropertyDefinition<T>, R: IsPropertyReference<T, W, *>> invoke(
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>, *>? = null,
        referenceGetter: P.() ->
            (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> R
    ) = referenceGetter(this.properties)(parent)

    /**
     * To get a top level reference on a model by passing a [propertyDefinitionGetter] from its defined Properties
     * Optionally pass an already resolved [parent]
     */
    @Suppress("UNCHECKED_CAST")
    fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *, *>> ref(
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>, *>? = null,
        propertyDefinitionGetter: P.()-> W
    ) =
        propertyDefinitionGetter(this.properties).getRef(parent) as IsPropertyReference<T, W, IsValues<P>>
}

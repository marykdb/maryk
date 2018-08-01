package maryk.core.models

import maryk.core.objects.AbstractValues
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** A DataModel which holds properties and can be validated */
interface IsDataModel<P: IsPropertyDefinitions> {
    /** Object which contains all property definitions. Can also be used to get property references. */
    val properties: P

    /** For quick notation to return [T] that operates with [runner] on Properties */
    fun <T: Any> props(
        runner: P.() -> T
    ) = runner(this.properties)

    /**
     * Get property reference fetcher of this DataModel with [referenceGetter]
     * Optionally pass an already resolved [parent]
     * For Strongly typed reference notation
     */
    operator fun <T: Any, W: IsPropertyDefinition<T>> invoke(
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>, *>? = null,
        referenceGetter: P.() ->
            (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> IsPropertyReference<T, W, *>
    ): IsPropertyReference<T, W, *> {
        return referenceGetter(this.properties)(parent)
    }

    /**
     * To get a top level reference on a model by passing a [propertyDefinitionGetter] from its defined Properties
     * Optionally pass an already resolved [parent]
     */
    fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *, AbstractValues<*, *, *>>> ref(
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>, *>? = null,
        propertyDefinitionGetter: P.()-> W
    ): IsPropertyReference<T, W, AbstractValues<*, *, *>> {
        @Suppress("UNCHECKED_CAST")
        return propertyDefinitionGetter(this.properties).getRef(parent) as IsPropertyReference<T, W, AbstractValues<*, *, *>>
    }

    /**
     * To get a top level reference on a model by passing a [propertyDefinitionGetter] from its defined Properties
     * Optionally pass an already resolved [parent]
     */
    fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *, *>> graph(
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?,
        propertyDefinitionGetter: P.()-> W
    ): IsPropertyReference<T, W, *> {
        @Suppress("UNCHECKED_CAST")
        return propertyDefinitionGetter(this.properties).getRef(parent) as IsPropertyReference<T, W, *>
    }
}

package maryk.core.models

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitionsCollectionDefinition
import maryk.core.properties.PropertyDefinitionsCollectionDefinitionWrapper
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

typealias IsSimpleDataModel = IsDataModel<IsPropertyDefinitions>

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
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>>? = null,
        referenceGetter: P.() ->
            (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> IsPropertyReference<T, W>
    ): IsPropertyReference<T, W> {
        return referenceGetter(this.properties)(parent)
    }

    /**
     * To get a top level reference on a model by passing a [propertyDefinitionGetter] from its defined Properties
     * Optionally pass an already resolved [parent]
     */
    fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *, *>> ref(
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>>? = null,
        propertyDefinitionGetter: P.()-> W
    ): IsPropertyReference<T, W> {
        @Suppress("UNCHECKED_CAST")
        return propertyDefinitionGetter(this.properties).getRef(parent) as IsPropertyReference<T, W>
    }

    /**
     * To get a top level reference on a model by passing a [propertyDefinitionGetter] from its defined Properties
     * Optionally pass an already resolved [parent]
     */
    fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *, *>> graph(
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>>?,
        propertyDefinitionGetter: P.()-> W
    ): IsPropertyReference<T, W> {
        @Suppress("UNCHECKED_CAST")
        return propertyDefinitionGetter(this.properties).getRef(parent) as IsPropertyReference<T, W>
    }

    companion object {
        internal fun <DM: IsDataModel<*>> addProperties(definitions: AbstractPropertyDefinitions<DM>): PropertyDefinitionsCollectionDefinitionWrapper<DM> {
            val wrapper = PropertyDefinitionsCollectionDefinitionWrapper<DM>(
                1,
                "properties",
                PropertyDefinitionsCollectionDefinition(
                    capturer = { context, propDefs ->
                        context?.apply {
                            this.propertyDefinitions = propDefs
                        } ?: ContextNotFoundException()
                    }
                )
            ) {
                @Suppress("UNCHECKED_CAST")
                it.properties as ObjectPropertyDefinitions<Any>
            }

            definitions.addSingle(wrapper)
            return wrapper
        }
    }
}

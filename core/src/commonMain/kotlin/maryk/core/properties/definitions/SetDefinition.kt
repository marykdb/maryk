package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.DefinitionsContext
import maryk.core.values.SimpleObjectValues

/** Definition for Set property */
data class SetDefinition<T: Any, CX: IsPropertyContext> internal constructor(
    override val indexed: Boolean = false,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val minSize: Int? = null,
    override val maxSize: Int? = null,
    override val valueDefinition: IsValueDefinition<T, CX>,
    override val default: Set<T>? = null
) : IsSetDefinition<T, CX> {
    override val propertyDefinitionType = PropertyDefinitionType.Set

    init {
        require(valueDefinition.required) { "Definition for value should have required=true on set" }
    }

    constructor(
        indexed: Boolean = false,
        required: Boolean = true,
        final: Boolean = false,
        minSize: Int? = null,
        maxSize: Int? = null,
        valueDefinition: IsSimpleValueDefinition<T, CX>,
        default: Set<T>? = null
    ): this(indexed, required, final, minSize, maxSize, valueDefinition as IsValueDefinition<T, CX>, default)

    override fun newMutableCollection(context: CX?) = mutableSetOf<T>()

    override fun getItemPropertyRefCreator(
        index: Int,
        item: T
    ) = { parentRef: AnyPropertyReference? ->
        @Suppress("UNCHECKED_CAST")
        this.getItemRef(item, parentRef as SetReference<T, CX>?) as IsPropertyReference<Any, *, *>
    }

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<Set<T>,IsPropertyDefinition<Set<T>>, *>?,
        newValue: Set<T>,
        validator: (item: T, parentRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>, *>?) -> Any
    ) {
        for (it in newValue) {
            validator(it) {
                @Suppress("UNCHECKED_CAST")
                this.getItemRef(it, refGetter() as SetReference<T, CX>?)
            }
        }
    }

    object Model : ContextualDataModel<SetDefinition<*, *>, ObjectPropertyDefinitions<SetDefinition<*, *>>, ContainsDefinitionsContext, SetDefinitionContext>(
        contextTransformer = { it: ContainsDefinitionsContext? -> SetDefinitionContext(it) },
        properties = object : ObjectPropertyDefinitions<SetDefinition<*, *>>() {
            init {
                IsPropertyDefinition.addIndexed(this, SetDefinition<*, *>::indexed)
                IsPropertyDefinition.addRequired(this, SetDefinition<*, *>::required)
                IsPropertyDefinition.addFinal(this, SetDefinition<*, *>::final)
                HasSizeDefinition.addMinSize(4, this, SetDefinition<*, *>::minSize)
                HasSizeDefinition.addMaxSize(5, this, SetDefinition<*, *>::maxSize)
                add(6, "valueDefinition",
                    ContextTransformerDefinition(
                        contextTransformer = { it?.definitionsContext },
                        definition = MultiTypeDefinition(
                            typeEnum = PropertyDefinitionType,
                            definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                        )
                    ),
                    getter = SetDefinition<*, *>::valueDefinition,
                    toSerializable = { value, _ ->
                        val defType = value!! as IsTransportablePropertyDefinitionType<*>
                        TypedValue(defType.propertyDefinitionType, value)
                    },
                    fromSerializable = {
                        @Suppress("UNCHECKED_CAST")
                        it?.value as IsValueDefinition<Any, DefinitionsContext>?
                    },
                    capturer = { context: SetDefinitionContext, value ->
                        @Suppress("UNCHECKED_CAST")
                        context.valueDefinion = value.value as IsValueDefinition<Any, ContainsDefinitionsContext>
                    }
                )
                @Suppress("UNCHECKED_CAST")
                add(7, "default", ContextualCollectionDefinition(
                    required = false,
                    contextualResolver = { context: SetDefinitionContext? ->
                        context?.setDefinition?.let {
                            it as IsByteTransportableCollection<Any, Collection<Any>, SetDefinitionContext>
                        } ?: throw ContextNotFoundException()
                    }
                ), SetDefinition<*, *>::default)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<SetDefinition<*, *>>) = SetDefinition(
            indexed = values(1),
            required = values(2),
            final = values(3),
            minSize = values(4),
            maxSize = values(5),
            valueDefinition = values<IsValueDefinition<*, *>>(6),
            default = values(7)
        )
    }
}

class SetDefinitionContext(
    val definitionsContext: ContainsDefinitionsContext?
) : IsPropertyContext {
    var valueDefinion: IsValueDefinition<Any, ContainsDefinitionsContext>? = null

    private var _setDefinition: Lazy<SetDefinition<Any, ContainsDefinitionsContext>> = lazy {
        SetDefinition(valueDefinition = this.valueDefinion ?: throw ContextNotFoundException())
    }

    val setDefinition: SetDefinition<Any, ContainsDefinitionsContext> get() = this._setDefinition.value
}

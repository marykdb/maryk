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
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val minSize: UInt? = null,
    override val maxSize: UInt? = null,
    override val valueDefinition: IsValueDefinition<T, CX>,
    override val default: Set<T>? = null
) : IsSetDefinition<T, CX> {
    override val propertyDefinitionType = PropertyDefinitionType.Set

    init {
        require(valueDefinition.required) { "Definition for value should have required=true on set" }
    }

    constructor(
        required: Boolean = true,
        final: Boolean = false,
        minSize: UInt? = null,
        maxSize: UInt? = null,
        valueDefinition: IsUsableInCollection<T, CX>,
        default: Set<T>? = null
    ): this(required, final, minSize, maxSize, valueDefinition as IsValueDefinition<T, CX>, default)

    override fun newMutableCollection(context: CX?) = mutableSetOf<T>()

    override fun getItemPropertyRefCreator(
        index: UInt,
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
        contextTransformer = { SetDefinitionContext(it) },
        properties = object : ObjectPropertyDefinitions<SetDefinition<*, *>>() {
            init {
                IsPropertyDefinition.addRequired(this, SetDefinition<*, *>::required)
                IsPropertyDefinition.addFinal(this, SetDefinition<*, *>::final)
                HasSizeDefinition.addMinSize(3, this, SetDefinition<*, *>::minSize)
                HasSizeDefinition.addMaxSize(4, this, SetDefinition<*, *>::maxSize)
                add(5, "valueDefinition",
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
                add(6, "default", ContextualCollectionDefinition(
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
            required = values(1),
            final = values(2),
            minSize = values(3),
            maxSize = values(4),
            valueDefinition = values<IsValueDefinition<*, *>>(5),
            default = values(6)
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

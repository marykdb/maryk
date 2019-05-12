package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.RequestException
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
data class SetDefinition<T : Any, CX : IsPropertyContext> internal constructor(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val minSize: UInt? = null,
    override val maxSize: UInt? = null,
    override val valueDefinition: IsValueDefinition<T, CX>,
    override val default: Set<T>? = null
) : IsSetDefinition<T, CX>,
    IsUsableInMapValue<Set<T>, CX>,
    IsUsableInMultiType<Set<T>, CX>,
    IsTransportablePropertyDefinitionType<Set<T>> {
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
    ) : this(required, final, minSize, maxSize, valueDefinition as IsValueDefinition<T, CX>, default)

    override fun newMutableCollection(context: CX?) = mutableSetOf<T>()

    override fun getItemPropertyRefCreator(
        index: UInt,
        item: T
    ) = { parentRef: AnyPropertyReference? ->
        @Suppress("UNCHECKED_CAST")
        this.itemRef(item, parentRef as SetReference<T, CX>?) as IsPropertyReference<Any, *, *>
    }

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>, *>?,
        newValue: Set<T>,
        validator: (item: T, parentRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>, *>?) -> Any
    ) {
        for (it in newValue) {
            validator(it) {
                @Suppress("UNCHECKED_CAST")
                this.itemRef(it, refGetter() as SetReference<T, CX>?)
            }
        }
    }

    object Model :
        ContextualDataModel<SetDefinition<*, *>, ObjectPropertyDefinitions<SetDefinition<*, *>>, ContainsDefinitionsContext, SetDefinitionContext>(
            contextTransformer = { SetDefinitionContext(it) },
            properties = object : ObjectPropertyDefinitions<SetDefinition<*, *>>() {
                init {
                    IsPropertyDefinition.addRequired(this, SetDefinition<*, *>::required)
                    IsPropertyDefinition.addFinal(this, SetDefinition<*, *>::final)
                    HasSizeDefinition.addMinSize(3u, this, SetDefinition<*, *>::minSize)
                    HasSizeDefinition.addMaxSize(4u, this, SetDefinition<*, *>::maxSize)
                    add(5u, "valueDefinition",
                        ContextTransformerDefinition(
                            contextTransformer = { it?.definitionsContext },
                            definition = InternalMultiTypeDefinition(
                                typeEnum = PropertyDefinitionType,
                                definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                            )
                        ),
                        getter = SetDefinition<*, *>::valueDefinition,
                        toSerializable = { value, _ ->
                            val defType = value as? IsTransportablePropertyDefinitionType<*>
                                ?: throw RequestException("$value is not transportable")
                            TypedValue(defType.propertyDefinitionType, defType)
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
                    add(6u, "default", ContextualCollectionDefinition(
                        required = false,
                        contextualResolver = { context: SetDefinitionContext? ->
                            context?.setDefinition?.let {
                                it as IsSerializablePropertyDefinition<Collection<Any>, SetDefinitionContext>
                            } ?: throw ContextNotFoundException()
                        }
                    ), SetDefinition<*, *>::default)
                }
            }
        ) {
        override fun invoke(values: SimpleObjectValues<SetDefinition<*, *>>) = SetDefinition(
            required = values(1u),
            final = values(2u),
            minSize = values(3u),
            maxSize = values(4u),
            valueDefinition = values<IsValueDefinition<*, *>>(5u),
            default = values(6u)
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

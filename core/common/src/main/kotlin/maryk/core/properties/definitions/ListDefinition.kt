package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelContext

/** Definition for List property */
data class ListDefinition<T: Any, CX: IsPropertyContext>(
    override val indexed: Boolean = false,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val minSize: Int? = null,
    override val maxSize: Int? = null,
    override val valueDefinition: IsValueDefinition<T, CX>,
    override val default: List<T>? = null
) : IsCollectionDefinition<T, List<T>, CX, IsValueDefinition<T, CX>>, HasDefaultValueDefinition<List<T>> {
    override val propertyDefinitionType = PropertyDefinitionType.List

    init {
        require(valueDefinition.required) { "Definition for value should have required=true on List" }
    }

    override fun newMutableCollection(context: CX?) = mutableListOf<T>()

    /** Get a reference to a specific list item on [parentList] by [index]. */
    fun getItemRef(index: Int, parentList: ListReference<T, CX>?) =
        ListItemReference(index, this, parentList)

    override fun validateCollectionForExceptions(refGetter: () -> IsPropertyReference<List<T>, IsPropertyDefinition<List<T>>>?, newValue: List<T>, validator: (item: T, parentRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>>?) -> Any) {
        newValue.forEachIndexed { index, item ->
            validator(item) {
                @Suppress("UNCHECKED_CAST")
                this.getItemRef(index, refGetter() as ListReference<T, CX>?)
            }
        }
    }

    object Model : ContextualDataModel<ListDefinition<*, *>, ObjectPropertyDefinitions<ListDefinition<*, *>>, DataModelContext, ListDefinitionContext>(
        contextTransformer = { it: DataModelContext? -> ListDefinitionContext(it) },
        properties = object : ObjectPropertyDefinitions<ListDefinition<*, *>>() {
            init {
                IsPropertyDefinition.addIndexed(this, ListDefinition<*, *>::indexed)
                IsPropertyDefinition.addRequired(this, ListDefinition<*, *>::required)
                IsPropertyDefinition.addFinal(this, ListDefinition<*, *>::final)
                HasSizeDefinition.addMinSize(3, this, ListDefinition<*, *>::minSize)
                HasSizeDefinition.addMaxSize(4, this, ListDefinition<*, *>::maxSize)
                add(5, "valueDefinition",
                    ContextTransformerDefinition(
                        contextTransformer = { it?.dataModelContext },
                        definition = MultiTypeDefinition(
                            typeEnum = PropertyDefinitionType,
                            definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                        )
                    ),
                    getter = ListDefinition<*, *>::valueDefinition,
                    toSerializable = { value, _ ->
                        val defType = value!! as IsTransportablePropertyDefinitionType<*>
                        TypedValue(defType.propertyDefinitionType, value)
                    },
                    fromSerializable = {
                        @Suppress("UNCHECKED_CAST")
                        it?.value as IsValueDefinition<Any, DataModelContext>?
                    },
                    capturer = { context: ListDefinitionContext?, value ->
                        context?.apply {
                            @Suppress("UNCHECKED_CAST")
                            valueDefinion = value.value as IsValueDefinition<Any, DataModelContext>
                        } ?: throw ContextNotFoundException()
                    }
                )
                @Suppress("UNCHECKED_CAST")
                add(6, "default", ContextualCollectionDefinition(
                    required = false,
                    contextualResolver = { context: ListDefinitionContext? ->
                        context?.listDefinition?.let {
                            it as IsByteTransportableCollection<Any, Collection<Any>, ListDefinitionContext>
                        } ?: throw ContextNotFoundException()
                    }
                ), ListDefinition<*, *>::default)
            }
        }
    ) {
        override fun invoke(map: SimpleValues<ListDefinition<*, *>>) = ListDefinition(
            indexed = map(0),
            required = map(1),
            final = map(2),
            minSize = map(3),
            maxSize = map(4),
            valueDefinition = map<IsValueDefinition<*, *>>(5),
            default = map(6)
        )
    }
}

class ListDefinitionContext(
    val dataModelContext: DataModelContext?
) : IsPropertyContext {
    var valueDefinion: IsValueDefinition<Any, DataModelContext>? = null

    private var _listDefinition: Lazy<ListDefinition<Any, DataModelContext>> = lazy {
        ListDefinition(valueDefinition = this.valueDefinion ?: throw ContextNotFoundException())
    }

    val listDefinition: ListDefinition<Any, DataModelContext> get() = this._listDefinition.value
}

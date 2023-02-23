package maryk.core.properties

import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Version
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.core.values.Values

open class RootModel<P: IsValuesPropertyDefinitions>(
    keyDefinition: () -> IsIndexable = { UUIDKey },
    version: Version = Version(1),
    indices: (() -> List<IsIndexable>)? = null,
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
    name: String? = null,
) : TypedPropertyDefinitions<RootDataModel<P>, P>(){
    @Suppress("UNCHECKED_CAST")
    override val Model: RootDataModel<P> by lazy {
        RootDataModel(
            keyDefinition = keyDefinition.invoke(),
            version = version,
            indices = indices?.invoke(),
            reservedIndices = reservedIndices,
            reservedNames = reservedNames,
            name = name ?: this::class.simpleName!!,
            properties = this,
        ) as RootDataModel<P>
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> invoke(
        parent: AnyOutPropertyReference? = null,
        referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
    ) = referenceGetter(this as P)(parent)

    operator fun <R> invoke(block: P.() -> R): R {
        @Suppress("UNCHECKED_CAST")
        return block(this as P)
    }

    fun create(
        vararg pairs: ValueItem?,
        setDefaults: Boolean = true,
    ) = Model.values {
        MutableValueItems().also { items ->
            for (pair in pairs) {
                if (pair != null) items += pair
            }
            if (setDefaults) {
                for (definition in this.allWithDefaults) {
                    val innerDef = definition.definition
                    if (items[definition.index] == null) {
                        items[definition.index] = (innerDef as HasDefaultValueDefinition<*>).default!!
                    }
                }
            }
        }
    }

    fun key(base64: String) = Model.key(base64)

    fun key(reader: () -> Byte) = Model.key(reader)

    fun key(bytes: ByteArray) = Model.key(bytes)

    fun key(values: Values<P>) = Model.key(values)

    /**
     * Create Property reference graph with list of graphables that are generated with [runner] on Properties
     * The graphables are sorted after generation so the RootPropRefGraph can be processed quicker.
     */
    fun graph(
        runner: P.() -> List<IsPropRefGraphNode<P>>
    ) = Model.graph(runner)
}

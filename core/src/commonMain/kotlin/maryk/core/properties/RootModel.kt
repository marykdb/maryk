package maryk.core.properties

import maryk.core.extensions.bytes.initByteArray
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.Version
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.core.values.Values
import maryk.lib.exceptions.ParseException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface IsRootModel: IsValuesPropertyDefinitions {
    override val Model: IsRootDataModel<out IsValuesPropertyDefinitions>
}

open class RootModel<DM: IsValuesPropertyDefinitions>(
    keyDefinition: () -> IsIndexable = { UUIDKey },
    version: Version = Version(1),
    indices: (() -> List<IsIndexable>)? = null,
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
    name: String? = null,
) : TypedPropertyDefinitions<RootDataModel<DM>, DM>(), IsRootModel {
    @Suppress("UNCHECKED_CAST")
    override val Model: RootDataModel<DM> by lazy {
        RootDataModel(
            keyDefinition = keyDefinition.invoke(),
            version = version,
            indices = indices?.invoke(),
            reservedIndices = reservedIndices,
            reservedNames = reservedNames,
            name = name ?: this::class.simpleName!!,
            properties = this,
        ) as RootDataModel<DM>
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> invoke(
        parent: AnyOutPropertyReference? = null,
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ) = referenceGetter(this as DM)(parent)

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
}

@OptIn(ExperimentalEncodingApi::class)
fun <DM: IsRootModel> DM.key(base64: String) = key(Base64.Mime.decode(base64))

@Suppress("UNCHECKED_CAST")
fun <DM: IsRootModel> DM.key(reader: () -> Byte) = Key<DM>(
    initByteArray(Model.keyByteSize, reader)
)

fun <DM: IsRootModel> DM.key(bytes: ByteArray): Key<DM> {
    if (bytes.size != Model.keyByteSize) {
        throw ParseException("Invalid byte length for key. Expected ${ Model.keyByteSize } instead of ${bytes.size}")
    }
    return Key(bytes)
}

/**
 * Create Property reference graph with list of graphables that are generated with [runner] on Properties
 * The graphables are sorted after generation so the RootPropRefGraph can be processed quicker.
 */
fun <DM : IsRootModel> DM.graph(
    runner: DM.() -> List<IsPropRefGraphNode<DM>>
) = RootPropRefGraph(runner(this).sortedBy { it.index })

/** Get Key based on [values] */
fun <DM : IsRootModel> DM.key(values: Values<DM>): Key<DM> {
    val bytes = ByteArray(this.Model.keyByteSize)
    var index = 0
    when (val keyDef = this.Model.keyDefinition) {
        is Multiple -> {
            keyDef.writeStorageBytes(values) {
                bytes[index++] = it
            }
        }
        is IsFixedBytesPropertyReference<out Any> -> {
            val value = keyDef.getValue(values)

            @Suppress("UNCHECKED_CAST")
            (keyDef as IsFixedStorageBytesEncodable<Any>).writeStorageBytes(value) {
                bytes[index++] = it
            }
        }
    }

    return Key(bytes)
}

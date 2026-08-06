package maryk.core.models

import maryk.core.base64.Base64Maryk
import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.initByteArray
import maryk.core.models.definitions.IsRootDataModelDefinition
import maryk.core.properties.definitions.index.GeoHash
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Normalize
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.checkKeyDefinitionAndCountBytes
import maryk.core.properties.definitions.index.normalizeStringForIndex
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.geoHashBits
import maryk.core.query.RequestContext
import maryk.core.query.changes.IsChange
import maryk.core.values.IsValuesGetter
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItems
import maryk.core.values.Values
import maryk.lib.exceptions.ParseException

/**
 * The root DataModel which can be stored in a DataStore.
 */
interface IsRootDataModel: IsValuesDataModel {
    override val Meta: IsRootDataModelDefinition
}

/** Create a Values object with given [changes] */
fun <DM : IsRootDataModel> DM.fromChanges(
    context: RequestContext?,
    changes: List<IsChange>
) = if (changes.isEmpty()) {
    Values(this, ValueItems(), context)
} else {
    val valueItemsToChange = MutableValueItems(mutableListOf())

    for (change in changes) {
        change.changeValues { ref, valueChanger ->
            valueItemsToChange.copyFromOriginalAndChange(null, ref.index, valueChanger)
        }
    }

    Values(this, valueItemsToChange, context)
}


fun <DM: IsRootDataModel> DM.key(base64: String) = key(Base64Maryk.decode(base64))

fun <DM: IsRootDataModel> DM.key(reader: () -> Byte) = Key<DM>(
    initByteArray(Meta.keyByteSize, reader)
)

fun <DM: IsRootDataModel> DM.key(bytes: ByteArray): Key<DM> {
    if (bytes.size != Meta.keyByteSize) {
        throw ParseException("Invalid byte length for key. Expected ${ Meta.keyByteSize } instead of ${bytes.size}")
    }
    return Key(bytes)
}

/**
 * Create Property reference graph with list of graphables that are generated with [runner] on Properties
 * The graphables are sorted after generation so the RootPropRefGraph can be processed quicker.
 */
fun <DM : IsRootDataModel> DM.graph(
    runner: DM.() -> List<IsPropRefGraphNode<DM>>
) = RootPropRefGraph(runner(this).sortedBy { it.index })

/** Get Key based on [values] */
fun <DM : IsRootDataModel> DM.key(values: Values<DM>): Key<DM> {
    return Key(this.Meta.keyDefinition.toKeyStorageBytes(values, this.Meta.keyByteSize))
}

private fun IsIndexable.toKeyStorageBytes(
    values: IsValuesGetter,
    expectedByteSize: Int,
): ByteArray = when (this) {
    is Multiple -> encodeKeyBytes(expectedByteSize) { writer ->
        references.forEach { reference ->
            reference.toKeyStorageBytes(
                values,
                checkKeyDefinitionAndCountBytes(reference),
            ).forEach(writer)
        }
    }
    is Normalize -> {
        val value = reference.getValueOrNull(values) ?: throw RequiredException(null)
        encodeKeyBytes(expectedByteSize) { writer ->
            writeStorageBytes(normalizeStringForIndex(value), writer)
        }
    }
    is GeoHash -> {
        val value = reference.getValueOrNull(values) ?: throw RequiredException(null)
        encodeKeyBytes(expectedByteSize) { writer ->
            value.geoHashBits(precisionBits).forEach(writer)
        }
    }
    is Reversed<*> -> encodeKeyBytes(expectedByteSize) { writer ->
        writeStorageBytes(values, writer)
    }
    is IsFixedBytesPropertyReference<*> -> encodeKeyBytes(expectedByteSize) { writer ->
        writeStorageBytes(values, writer)
    }
    else -> throw TypeException("Unknown root key IsIndexable type: $this")
}

private fun encodeKeyBytes(
    expectedByteSize: Int,
    encoder: ((Byte) -> Unit) -> Unit,
): ByteArray {
    val bytes = mutableListOf<Byte>()
    encoder { bytes += it }

    if (bytes.size != expectedByteSize) {
        throw ParseException(
            "Invalid runtime byte length for key. Expected $expectedByteSize instead of ${bytes.size}"
        )
    }

    return bytes.toByteArray()
}

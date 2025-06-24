package maryk.core.models

import maryk.core.base64.Base64Maryk
import maryk.core.extensions.bytes.initByteArray
import maryk.core.models.definitions.IsRootDataModelDefinition
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.core.query.changes.IsChange
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
    val bytes = ByteArray(this.Meta.keyByteSize)
    var index = 0
    when (val keyDef = this.Meta.keyDefinition) {
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

package maryk.core.models

import maryk.core.base64.Base64Maryk
import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.initByteArray
import maryk.core.models.definitions.IsRootDataModelDefinition
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.index.GeoHash
import maryk.core.properties.definitions.index.encodeIndexValue
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Normalize
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.checkKeyDefinitionAndCountBytes
import maryk.core.properties.definitions.index.normalizeStringForIndex
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.toListIndex
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.geoHashBits
import maryk.core.query.RequestContext
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.Change
import maryk.core.query.changes.SetChange
import maryk.core.query.pairs.IsReferenceValueOrNullPair
import maryk.core.query.pairs.ReferenceValuePair
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
): Values<DM> {
    var values = Values(this, ValueItems(), context)
    for (change in changes) {
        if (change is Change) {
            for (pair in change.referenceValuePairs) {
                values = values.withCreationParents(pair).change(Change(pair))
            }
        } else if (change is SetChange) {
            for (setChange in change.setValueChanges) {
                @Suppress("UNCHECKED_CAST")
                val reference = setChange.reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
                val empty = ReferenceValuePair(reference, emptySet<Any>())
                values = values.withCreationParents(empty)
                if (values[reference] == null) values = values.change(Change(empty))
                values = values.change(SetChange(setChange))
            }
        } else {
            values = values.change(change)
        }
    }
    return values
}

/** Creation logs can name children before their structural parents exist. */
@Suppress("UNCHECKED_CAST")
private fun <DM : IsRootDataModel> Values<DM>.withCreationParents(pair: IsReferenceValueOrNullPair<Any>): Values<DM> {
    val value = pair.value ?: return this
    val references = pair.reference.unwrap()
    var result = this
    for (index in references.indices) {
        val reference = references[index] as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>
        val current = result[reference]
        val next = references.getOrNull(index + 1)
        val mismatchedType = next is TypedValueReference<*, *, *> && current is TypedValue<*, *> && current.type != next.type
        if (current == null || mismatchedType) {
            if (index == references.lastIndex && reference !is ListItemReference<*, *>) continue
            val structure = creationStructure(references, index, value, context) ?: continue
            if (reference is ListItemReference<*, *>) {
                val parent = reference.parentReference ?: continue
                val list = result[parent] as? List<*> ?: continue
                // Creation rows are ordered by unsigned list index. Do not invent sparse values.
                if (reference.index.toListIndex() == list.size) {
                    result = result.change(Change(ReferenceValuePair(
                        parent as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>,
                        list + structure,
                    )))
                    continue
                }
            }
            result = result.change(Change(ReferenceValuePair(
                reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>,
                structure,
            )))
        }
    }
    return result
}

@Suppress("UNCHECKED_CAST")
private fun creationStructure(references: List<AnyPropertyReference>, index: Int, value: Any, context: RequestContext?): Any? {
    if (index == references.lastIndex) return value
    val property = references[index].propertyDefinition
    return when (val definition = if (property is IsDefinitionWrapper<*, *, *, *>) property.definition else property) {
        is IsEmbeddedValuesDefinition<*, *> -> Values(definition.dataModel, MutableValueItems(), context)
        is IsListDefinition<*, *> -> mutableListOf<Any>()
        is IsMapDefinition<*, *, *> -> mutableMapOf<Any, Any>()
        is IsSetDefinition<*, *> -> mutableSetOf<Any>()
        is IsMultiTypeDefinition<*, *, *> -> {
            val next = references[index + 1] as? TypedValueReference<*, *, *> ?: return null
            val child = creationStructure(references, index + 1, value, context) ?: return null
            TypedValue(next.type, child)
        }
        else -> null
    }
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
        writeKeyComponent(values, writer)
    }
    is IsFixedBytesPropertyReference<*> -> encodeKeyBytes(expectedByteSize) { writer ->
        writeKeyComponent(values, writer)
    }
    else -> throw TypeException("Unknown root key IsIndexable type: $this")
}

private fun <T : Any> IsIndexablePropertyReference<T>.writeKeyComponent(values: IsValuesGetter, writer: (Byte) -> Unit) =
    encodeIndexValue(getValue(values)).forEach(writer)

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

package maryk.datastore.shared

import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.readStorageToValues
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.SimpleTypedValueReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.properties.types.Bytes
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart

/** Exists inspects presence markers, including encrypted scalars, without reading their payloads. */
fun hasStoredReferenceValue(reference: AnyPropertyReference, bytes: ByteArray, offset: Int, length: Int): Boolean {
    val definition = reference.comparablePropertyDefinition.let { if (it is IsDefinitionWrapper<*, *, *, *>) it.definition else it }
    if (!hasStoredValuePayload(definition, bytes, offset, length)) return false
    if (definition is IsCollectionDefinition<*, *, *, *> || definition is IsMapDefinition<*, *, *>) {
        return readStoredCount(bytes, offset, length) != null
    }
    if (reference is SimpleTypedValueReference<*, *, *>) {
        var readIndex = offset
        return initUIntByVarWithExtraInfo({ bytes[readIndex++] }) { type, _ -> type == reference.type.index }
    }
    if (definition is IsEmbeddedValuesDefinition<*, *>) {
        var readIndex = offset
        return readValue(definition, { bytes[readIndex++] }) { offset + length - readIndex } != null
    }
    return true
}

fun storageReferenceParents(reference: AnyPropertyReference): List<AnyPropertyReference> {
    val first = (reference as? IsPropertyReferenceWithParent<*, *, *, *>)?.parentReference ?: return emptyList()
    return buildList {
        var parent: AnyPropertyReference? = first
        while (parent != null) {
            add(parent)
            parent = (parent as? IsPropertyReferenceWithParent<*, *, *, *>)?.parentReference
        }
    }
}

/** A type can leave old child cells behind; only its current case is logically present. */
fun hasActiveStoredType(reference: AnyPropertyReference, readTypeIndex: (AnyPropertyReference) -> UInt?): Boolean {
    if (reference !is TypedValueReference<*, *, *> && reference !is SimpleTypedValueReference<*, *, *> &&
        (reference as? IsPropertyReferenceWithParent<*, *, *, *>)?.parentReference == null) return true
    return (listOf(reference) + storageReferenceParents(reference)).asReversed().all { part ->
        when (part) {
            is TypedValueReference<*, *, *> -> part.parentReference?.let { readTypeIndex(it) == part.type.index } == true
            is SimpleTypedValueReference<*, *, *> -> part.parentReference?.let { readTypeIndex(it) == part.type.index } == true
            else -> true
        }
    }
}

/** Read just the discriminator, without decoding the value belonging to the selected case. */
fun storedTypeIndex(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): UInt? {
    if (length == 0) return null
    var index = offset
    return initUIntByVarWithExtraInfo({ bytes[index++] }) { type, indicator ->
        type.takeIf { indicator == TypeIndicator.SimpleTypeIndicator.byte || indicator == TypeIndicator.ComplexTypeIndicator.byte }
    }
}

fun requiresStorageParentMarker(reference: AnyPropertyReference): Boolean = reference !is TypedValueReference<*, *, *> &&
    requiresStoredReferenceStructure(reference)

fun requiresStoredReferenceStructure(reference: AnyPropertyReference): Boolean =
    reference.comparablePropertyDefinition.let { if (it is IsDefinitionWrapper<*, *, *, *>) it.definition else it }.let { definition ->
        definition is IsEmbeddedValuesDefinition<*, *> || definition is IsCollectionDefinition<*, *, *, *> ||
            definition is IsMapDefinition<*, *, *> || definition is IsMultiTypeDefinition<*, *, *>
    }

/** Decode only the requested subtree and its actual ancestor markers; no defaults are synthesized. */
fun IsRootDataModel.readStoredReferenceValue(reference: AnyPropertyReference, rows: List<Pair<ByteArray, ByteArray>>): Any? {
    val parents = storageReferenceParents(reference)
    val selectedListItems = (parents + reference).filterIsInstance<ListItemReference<*, *>>()
    val projectedCounts = selectedListItems.map { item ->
        val parent = requireNotNull(item.parentReference).toStorageByteArray()
        val countBytes = rows.firstOrNull { it.first.contentEquals(parent) }?.second ?: return null
        val count = readStoredCount(countBytes) ?: return null
        if (item.index >= count.toUInt()) return null
        Bytes(parent)
    }.toMutableSet()
    for (parent in parents) {
        val definition = parent.comparablePropertyDefinition.let { if (it is IsDefinitionWrapper<*, *, *, *>) it.definition else it }
        if (definition !is IsMapDefinition<*, *, *>) continue
        val qualifier = parent.toStorageByteArray()
        val countBytes = rows.firstOrNull { it.first.contentEquals(qualifier) }?.second ?: return null
        if ((readStoredCount(countBytes) ?: return null) == 0) return null
        projectedCounts += Bytes(qualifier)
    }
    val listProjections = selectedListItems.map { item ->
        item.toStorageByteArray() to requireNotNull(item.parentReference).calculateStorageByteLength()
    }
    fun projectQualifier(qualifier: ByteArray): ByteArray = if (listProjections.isEmpty()) qualifier else qualifier.copyOf().also { projected ->
        for ((prefix, indexOffset) in listProjections) {
            if (qualifier.matchesRangePart(0, prefix)) {
                repeat(UInt.SIZE_BYTES) { projected[indexOffset + it] = 0 }
            }
        }
    }
    // Each selected list ancestor becomes a one-item view. Validate the real bounds above,
    // then remap only copied qualifiers/counts; unselected siblings need not be read or decrypted.
    val projectedRows = if (listProjections.isEmpty() && projectedCounts.isEmpty()) rows else {
        rows.map { (qualifier, value) ->
            projectQualifier(qualifier) to if (projectedCounts.isNotEmpty() && Bytes(qualifier) in projectedCounts) byteArrayOf(1) else value
        }
    }
    val projectedReference = if (selectedListItems.isEmpty()) reference else {
        val qualifier = projectQualifier(reference.toStorageByteArray())
        var index = 0
        getPropertyReferenceByStorageBytes(qualifier.size, { qualifier[index++] })
    }
    val orderedUnique = (1 until projectedRows.size).all { index ->
        projectedRows[index - 1].first.compareTo(projectedRows[index].first) < 0
    }
    val sorted = if (orderedUnique) projectedRows else
        projectedRows.distinctBy { Bytes(it.first) }.sortedWith { first, second -> first.first.compareTo(second.first) }
    var rowIndex = -1
    val values = readStorageToValues(
        getQualifier = { reader ->
            sorted.getOrNull(++rowIndex)?.first?.let { qualifier -> reader({ qualifier[it] }, qualifier.size); true } ?: false
        },
        select = null,
        processValue = { type, property ->
            val bytes = sorted[rowIndex].second
            when {
                type == ObjectDelete || isStoredPropertyDeleteMarker(bytes) || bytes.isEmpty() -> null
                type == ListSize || type == SetSize || type == MapSize -> readStoredCount(bytes)
                else -> {
                    var readIndex = 0
                    val definition = property.propertyDefinition.let { if (it is IsDefinitionWrapper<*, *, *, *>) it.definition else it }
                    readValue(definition, { bytes[readIndex++] }) { bytes.size - readIndex }
                }
            }
        },
    )
    @Suppress("UNCHECKED_CAST")
    return values[projectedReference as IsPropertyReference<Any, *, *>]
}

@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore

import maryk.core.extensions.bytes.initIntByVarWithExtraInfo
import maryk.core.models.IsDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.CompleteReferenceType.DELETE
import maryk.core.properties.references.CompleteReferenceType.MAP_KEY
import maryk.core.properties.references.CompleteReferenceType.TYPE
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ReferenceType.LIST
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.properties.references.ReferenceType.SPECIAL
import maryk.core.properties.references.ReferenceType.VALUE
import maryk.core.properties.references.completeReferenceTypeOf
import maryk.core.properties.references.referenceStorageTypeOf
import maryk.core.query.changes.Change
import maryk.core.query.changes.Delete
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.ReferenceValuePair

typealias ValueWithVersionReader = (StorageTypeEnum<IsPropertyDefinition<Any>>, IsPropertyDefinition<Any>?, (ULong, Any?) -> Unit) -> Unit
private typealias ChangeAdder = (ULong, IsChange) -> Unit

/**
 * Convert storage bytes to values.
 * [getQualifier] gets a qualifier until none is available and returns null
 * [processValue] processes the storage value with given type and definition
 */
fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> DM.convertStorageToChanges(
    getQualifier: () -> ByteArray?,
    select: RootPropRefGraph<P>?,
    processValue: ValueWithVersionReader
): List<VersionedChanges> {
    // Used to collect all found ValueItems
    val mutableVersionedChanges = mutableListOf<VersionedChanges>()

    // Adds changes to versionedChangesCollection
    val changeAdder: ChangeAdder = { version: ULong, change: IsChange ->
        val index = mutableVersionedChanges.binarySearch { it.version.compareTo(version) }

        if (index < 0) {
            mutableVersionedChanges.add(
                (index * -1) - 1,
                VersionedChanges(version, mutableListOf(change))
            )
        } else {
            (mutableVersionedChanges[index].changes as MutableList<IsChange>).add(change)
        }
    }

    processQualifiers(getQualifier) { qualifier, addToCache ->
        // Otherwise try to get a new qualifier processor from DataModel
        (this as IsDataModel<P>).readQualifier(qualifier, 0, select, changeAdder, processValue, addToCache)
    }

    // Create Values
    return mutableVersionedChanges
}

/**
 * Read specific [qualifier] from [offset].
 * [addChangeToOutput] is used to add changes to output
 * [readValueFromStorage] is used to fetch actual value from storage layer
 * [addToCache] is used to add a sub reader to cache so it does not need to reprocess qualifier from start
 */
private fun <P: PropertyDefinitions> IsDataModel<P>.readQualifier(
    qualifier: ByteArray,
    offset: Int,
    select: IsPropRefGraph<P>?,
    addChangeToOutput: ChangeAdder,
    readValueFromStorage: ValueWithVersionReader,
    addToCache: CacheProcessor
) {
    var qIndex = offset

    initIntByVarWithExtraInfo({ qualifier[qIndex++] }) { index, type ->
        val subSelect = select?.selectNodeOrNull(index)

        if (select != null && subSelect == null) {
            // Return null if not selected within select
            null
        } else {
            val isAtEnd = qualifier.size <= qIndex
            when (referenceStorageTypeOf(type)) {
                SPECIAL -> when (val specialType = completeReferenceTypeOf(qualifier[offset])) {
                    DELETE -> TODO("Implement object delete")
                    TYPE, MAP_KEY -> throw Exception("Cannot handle Special type $specialType in qualifier")
                    else -> throw Exception("Not recognized special type $specialType")
                }
                VALUE -> readValue(isAtEnd, index, qualifier, select, qIndex, addChangeToOutput, readValueFromStorage, addToCache)
                LIST -> TODO("Implement List")
                SET -> TODO("Implement Set")
                MAP -> TODO("Implement Map")
            }
        }
    }
}

/**
 * Reads a specific value type
 * [isAtEnd] if qualifier is at the end
 * [index] of the item to be injected in output
 * [qualifier] in storage of current value
 * [select] to only process selected values
 * [offset] in bytes from where qualifier
 * [addChangeToOutput] / [readValueFromStorage]
 * [addToCache] so next qualifiers do not need to reprocess qualifier
 */
@Suppress("UNUSED_PARAMETER")
private fun <P : PropertyDefinitions> IsDataModel<P>.readValue(
    isAtEnd: Boolean,
    index: Int,
    qualifier: ByteArray,
    select: IsPropRefGraph<P>?,
    offset: Int,
    addChangeToOutput: ChangeAdder,
    readValueFromStorage: ValueWithVersionReader,
    addToCache: CacheProcessor
) {
    if (isAtEnd) {
        val propDefinition = this.properties[index]!!

        @Suppress("UNCHECKED_CAST")
        readValueFromStorage(
            Value as StorageTypeEnum<IsPropertyDefinition<Any>>,
            propDefinition
        ) { version, value ->
            val ref = propDefinition.getRef() as IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, *, IsPropertyContext, *>, *>
            if (value != null) {
                addChangeToOutput(version, Change(ReferenceValuePair(ref, value)))
            } else {
                addChangeToOutput(version, Delete(ref))
            }
        }
    } else {
        TODO("Implement complex")
    }
}


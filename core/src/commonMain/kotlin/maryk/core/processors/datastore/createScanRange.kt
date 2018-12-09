package maryk.core.processors.datastore

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.filters.Equals
import maryk.core.query.filters.IsFilter
import maryk.lib.extensions.compare.compareTo

fun <DM: IsRootValuesDataModel<*>> DM.createScanRange(filter: IsFilter?, startKey: ByteArray?): ScanRange {
    val listOfParts = mutableListOf<IsPartialToMatch>()

    convertFilter(this, filter, listOfParts)

    listOfParts.sortBy { it.fromIndex }

    return this.createScanRangeFromParts(startKey, listOfParts)
}

private fun <DM: IsRootValuesDataModel<*>> DM.createScanRangeFromParts(
    startKey: ByteArray?,
    listOfParts: MutableList<IsPartialToMatch>
): ScanRange {
    val start = ByteArray(this.keySize)
    val end = ByteArray(this.keySize)

    var currentOffset = 0
    var keyIndex = 0
    val toRemove = mutableListOf<Int>()
    for ((keyPartIndex, keyPart) in listOfParts.withIndex()) {
        val nextIndex = this.keyIndices[keyIndex++]
        if (currentOffset != keyPart.fromIndex) {
            break
        }
        when (keyPart) {
            is PartialToMatch -> {
                keyPart.toMatch.forEachIndexed { i, b ->
                    start[i + currentOffset] = b
                    end[i + currentOffset] = b
                }
                if (nextIndex > 0) {
                    start[nextIndex - 1] = 1 // Separator so match is exact
                    end[nextIndex - 1] = 1 // Separator so match is exact
                }
                toRemove.add(keyPartIndex)
            }
            else -> throw Exception("Unknown partial type")
        }
        if (keyIndex < this.keyDefinitions.size) {
            currentOffset = nextIndex
        }
    }

    val endToPass = when {
        keyIndex <= 0 -> null
        keyIndex < this.keyDefinitions.size -> {
            end[this.keyIndices[keyIndex] - 1] = 2 // Separator at 2 so end is always past exact match
            end
        }
        else -> end
    }

    for (partToRemoveIndex in toRemove) {
        listOfParts.removeAt(partToRemoveIndex)
    }

    return ScanRange(
        start = if (startKey != null && start < startKey) startKey else start,
        end = endToPass,
        partialMatches = listOfParts
    )
}

fun convertFilter(
    dataModel: IsRootValuesDataModel<*>,
    filter: IsFilter?,
    listOfParts: MutableList<IsPartialToMatch>
) {
    when (filter) {
        null -> {} // Skip
        is Equals -> {
            for ((reference, value) in filter.referenceValuePairs) {
                getKeyDefinitionOrNull(dataModel, reference) { index, keyDefinition ->
                    var byteReadIndex = 0
                    val byteArray = ByteArray(keyDefinition.byteSize)
                    keyDefinition.writeStorageBytes(value) {
                        byteArray[byteReadIndex++] = it
                    }

                    listOfParts.add(
                        PartialToMatch(dataModel.keyIndices[index], byteArray)
                    )
                }
            }
        }
    }
}

private fun getKeyDefinitionOrNull(
    dataModel: IsRootValuesDataModel<*>,
    reference: IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, *, IsPropertyContext, *>, *>,
    processKeyDefinitionIfFound: (Int, FixedBytesProperty<Any>) -> Unit
){
    for ((index, keyDef) in dataModel.keyDefinitions.withIndex()) {
        if (keyDef.isForPropertyReference(reference)) {
            @Suppress("UNCHECKED_CAST")
            processKeyDefinitionIfFound(index, keyDef as FixedBytesProperty<Any>)
            break
        }
    }
}

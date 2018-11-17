@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.processors.datastore.memory.records

import maryk.core.models.IsValuesDataModel
import maryk.core.models.map
import maryk.core.values.Values
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.EmbeddedValuesPropertyDefinitionWrapper

internal inline class DataRecordValueTree<DM: IsValuesDataModel<P>, P: PropertyDefinitions>(
    internal val recordNodes: List<DataRecordNode>
) {
    fun toValues(dataModel: DM, handleValues: ((Values<DM, P>, ULong) -> Unit)) {
        val valuesMap = LinkedHashMap<Int, Any>(this.recordNodes.size)
        var maxVersion = 0uL
        for (node in this.recordNodes) {
            val index = node.index.toInt()
            @Suppress("UNCHECKED_CAST")
            when (node) {
                is DataRecordValue<*> -> {
                    valuesMap[index] = node.value
                    if (maxVersion < node.version) maxVersion = node.version
                }
                is DataRecordHistoricValues<*> -> {
                    val recordValue = node.history.last()
                    valuesMap[index] = recordValue.value
                    if (maxVersion < recordValue.version) maxVersion = recordValue.version
                }
                is DataRecordValueTreeNode<*, *> -> {
                    val embeddedValuesPropertyDefinitionWrapper = dataModel.properties[node.index.toInt()]
                        as? EmbeddedValuesPropertyDefinitionWrapper<IsValuesDataModel<PropertyDefinitions>, *, *, *>
                        ?: throw Exception("Cannot convert property at $index to Values")

                    (node as DataRecordValueTreeNode<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>).value.toValues(
                        embeddedValuesPropertyDefinitionWrapper.dataModel
                    ) { recordValue, maxRecordVersion ->
                        valuesMap[node.index.toInt()] = recordValue
                        if (maxVersion < maxRecordVersion) maxVersion = maxRecordVersion
                    }
                }
            }
        }

        handleValues(
            dataModel.map(context = null) {
                valuesMap
            },
            maxVersion
        )
    }
}

internal fun <DM: IsValuesDataModel<P>, P: PropertyDefinitions> Values<DM, P>.toDataRecordValueTree(version: ULong): DataRecordValueTree<DM, P> {
    val keys = this.keys.toIntArray()
    keys.sort()
    val nodes: List<DataRecordNode> = List(keys.size) {
        val key = keys[it]
        val value = this<Any>(key)

        if (value is Values<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val values = value as Values<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>
            DataRecordValueTreeNode(
                key.toUShort(),
                values.toDataRecordValueTree(version)
            )
        } else {
            DataRecordValue(
                index = key.toUShort(),
                value = value,
                version = version
            )
        }
    }

    return DataRecordValueTree(nodes)
}

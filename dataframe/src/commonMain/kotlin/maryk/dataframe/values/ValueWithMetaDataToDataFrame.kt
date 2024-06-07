package maryk.dataframe.values

import maryk.core.query.ValuesWithMetaData
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.columnOf
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.emptyDataFrame

fun ValuesWithMetaData<*>.toDataFrame(): AnyFrame {
    val headers = mutableMapOf(0u to "Key", 1u to "Values", 2u to "IsDeleted", 3u to "FirstVersion", 4u to "LastVersion")
    val dfValues = mutableMapOf<UInt, Any?>(0u to key.toString(), 1u to values.toDataFrame(), 2u to isDeleted, 3u to firstVersion, 4u to lastVersion)

    return dataFrameOf(headers.values)(dfValues.values.map {
        columnOf(it)
    })
}

fun List<ValuesWithMetaData<*>>.toDataFrame(): AnyFrame {
    if (this.isEmpty()) return emptyDataFrame<Any>()

    val headers = mutableMapOf(0u to "Key", 1u to "Values", 2u to "IsDeleted", 3u to "FirstVersion", 4u to "LastVersion")
    val dfValues = mutableMapOf<UInt, MutableList<Any?>>(0u to mutableListOf(), 1u to mutableListOf(), 2u to mutableListOf(), 3u to mutableListOf(), 4u to mutableListOf())

    this.forEach { valuesWithMeta ->
        dfValues.getOrPut(0u, ::mutableListOf).add(valuesWithMeta.key.toString())
        dfValues.getOrPut(1u, ::mutableListOf).add(valuesWithMeta.values.toDataFrame())
        dfValues.getOrPut(2u, ::mutableListOf).add(valuesWithMeta.isDeleted)
        dfValues.getOrPut(3u, ::mutableListOf).add(valuesWithMeta.firstVersion)
        dfValues.getOrPut(4u, ::mutableListOf).add(valuesWithMeta.lastVersion)
    }

    return dataFrameOf(headers.values)(dfValues.values.map { columnOf(it) })
}

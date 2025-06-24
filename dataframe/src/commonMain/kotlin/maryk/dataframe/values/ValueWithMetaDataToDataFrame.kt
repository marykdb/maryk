package maryk.dataframe.values

import maryk.core.query.ValuesWithMetaData
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.columnOf
import org.jetbrains.kotlinx.dataframe.api.concat
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.emptyDataFrame
import org.jetbrains.kotlinx.dataframe.api.named

fun ValuesWithMetaData<*>.toDataFrame(): AnyFrame {
    val key = columnOf(key.toString()) named "key"
    val values = columnOf(
        this.values.toDataFrame().columns()
    ) named "values"
    val isDeleted = columnOf(isDeleted) named "isDeleted"
    val firstVersion = columnOf(firstVersion) named "firstVersion"
    val lastVersion = columnOf(lastVersion) named "lastVersion"

    return dataFrameOf(key, values, isDeleted, firstVersion, lastVersion)
}

fun List<ValuesWithMetaData<*>>.toDataFrame(): AnyFrame {
    if (this.isEmpty()) return emptyDataFrame<Any>()

    val headers = mutableMapOf(0u to "Key", 1u to "Values", 2u to "IsDeleted", 3u to "FirstVersion", 4u to "LastVersion")
    val keys = ArrayList<String>(this.size)
    val values = ArrayList<DataFrame<*>>(this.size)
    val isDeleteds = ArrayList<Boolean>(this.size)
    val firstVersions = ArrayList<ULong>(this.size)
    val lastVersions = ArrayList<ULong>(this.size)

    this.forEach { valuesWithMeta ->
        keys.add(valuesWithMeta.key.toString())
        values.add(valuesWithMeta.values.toDataFrame())
        isDeleteds.add(valuesWithMeta.isDeleted)
        firstVersions.add(valuesWithMeta.firstVersion)
        lastVersions.add(valuesWithMeta.lastVersion)
    }

    return dataFrameOf(headers.values)(listOf(
        columnOf(*keys.toTypedArray()),
        columnOf(values.concat().columns()),
        columnOf(*isDeleteds.toTypedArray()),
        columnOf(*firstVersions.toTypedArray()),
        columnOf(*lastVersions.toTypedArray())
    ))
}

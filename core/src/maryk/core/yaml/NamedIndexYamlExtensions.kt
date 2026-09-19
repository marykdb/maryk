package maryk.core.yaml

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetDefinitionWrapper
import maryk.core.values.MutableValueItems
import maryk.json.IllegalJsonOperation
import maryk.json.JsonToken.EndComplexFieldName
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartComplexFieldName
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Value
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/** Write a complex field name with [index]: [name] as key value pair */
internal fun YamlWriter.writeNamedIndexField(
    name: String,
    index: UInt,
    alternativeNames: Set<String>? = null,
    sensitive: Boolean = false,
) {
    writeStartComplexField()
    writeStartObject()
    writeFieldName(index.toString())
    when {
        alternativeNames == null && !sensitive -> writeValue(name)
        else -> {
            writeStartArray(true)
            writeValue(name)
            alternativeNames?.forEach(::writeValue)
            if (sensitive) writeBoolean(true)
            writeEndArray()
        }
    }
    writeEndObject()
    writeEndComplexField()
}

/**
 * Read a complex named index field from yaml and write values
 * to [valueMap] using [nameDescriptor] and [indexDescriptor]
 */
internal fun <DO : Any> IsYamlReader.readNamedIndexField(
    valueMap: MutableValueItems,
    nameDescriptor: FlexBytesDefinitionWrapper<String, String, IsPropertyContext, StringDefinition, DO>,
    indexDescriptor: FixedBytesDefinitionWrapper<UInt, *, IsPropertyContext, NumberDefinition<UInt>, DO>,
    alternativeNamesDescriptor: SetDefinitionWrapper<String, IsPropertyContext, *>? = null,
    sensitiveDescriptor: FixedBytesDefinitionWrapper<Boolean, *, IsPropertyContext, BooleanDefinition, DO>? = null,
) {
    if (currentToken != StartComplexFieldName || nextToken() !is StartObject) {
        throw IllegalJsonOperation("Expected named index like '? [0: name]'")
    }

    val index = (nextToken() as? FieldName)?.value?.toUInt()
        ?: throw IllegalJsonOperation("Expected index integer as field name like '? 0: name'")
    valueMap[indexDescriptor.index] = index

    when (nextToken()) {
        is Value<*> -> valueMap[nameDescriptor.index] = nameDescriptor.readJson(this, null)
        is StartArray -> {
            if (nextToken() !is Value<*>) throw IllegalJsonOperation("Expected property name as value like '? 0: name'")
            valueMap[nameDescriptor.index] = nameDescriptor.readJson(this, null)

            val altNames = mutableSetOf<String>()
            while (nextToken() is Value<*>) {
                if ((currentToken as Value<*>).value is Boolean) {
                    if ((currentToken as Value<*>).value == true) {
                        sensitiveDescriptor?.let { valueMap[it.index] = true }
                    }
                    continue
                }
                alternativeNamesDescriptor?.let { altNames += it.valueDefinition.readJson(this, null) }
            }
            if (altNames.isNotEmpty()) {
                alternativeNamesDescriptor?.let { valueMap[it.index] = altNames }
            }
        }
        else -> throw IllegalJsonOperation("Expected property name as value like '? 0: name'")
    }

    if (nextToken() != EndObject || nextToken() != EndComplexFieldName) {
        throw IllegalJsonOperation("Expected only one index/value inside key like '? 0: name' Start descriptor ': '  on a line below in same indent as '?'")
    }
    nextToken() // Move to next value
}

package maryk.core.yaml

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.json.IllegalJsonOperation
import maryk.json.JsonToken
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/** Write a complex field name with [index]: [name] as key value pair */
internal fun YamlWriter.writeNamedIndexField(name: String, index: Int) {
    this.writeStartComplexField()
    this.writeStartObject()
    this.writeFieldName(index.toString())
    this.writeValue(name)
    this.writeEndObject()
    this.writeEndComplexField()
}

/**
 * Read a complex named index field from yaml and write values
 * to [valueMap] using [nameDescriptor] and [indexDescriptor]
 */
internal fun <DO: Any> IsYamlReader.readNamedIndexField(
    valueMap: MutableMap<Int, Any>,
    nameDescriptor: PropertyDefinitionWrapper<String, IsPropertyContext, StringDefinition, DO>,
    indexDescriptor: FixedBytesPropertyDefinitionWrapper<UInt32, IsPropertyContext, NumberDefinition<UInt32>, DO>
) {
    if (currentToken != JsonToken.StartComplexFieldName || nextToken() !is JsonToken.StartObject) {
        throw IllegalJsonOperation("Expected named index like '? [0: name]'")
    }

    val index = (nextToken() as? JsonToken.FieldName)?.value?.toInt()?.toUInt32()
            ?: throw IllegalJsonOperation("Expected index integer as field name like '? 0: name'")
    valueMap[indexDescriptor.index] = index

    (nextToken() as? JsonToken.Value<*>) ?: throw IllegalJsonOperation("Expected property name as value like '? 0: name'")
    valueMap[nameDescriptor.index] = nameDescriptor.readJson(this, null)

    if (nextToken() != JsonToken.EndObject || nextToken() != JsonToken.EndComplexFieldName){
        throw IllegalJsonOperation("Expected only one index/value inside key like '? [0: name]' Start descriptor ': '  on a line below in same indent as '?'")
    }
    nextToken() // Move to next value
}

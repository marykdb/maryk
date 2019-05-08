package maryk.core.yaml

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.values.MutableValueItems
import maryk.json.IllegalJsonOperation
import maryk.json.JsonToken.EndComplexFieldName
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartComplexFieldName
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Value
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/** Write a complex field name with [index]: [name] as key value pair */
internal fun YamlWriter.writeNamedIndexField(name: String, index: UInt) {
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
internal fun <DO : Any> IsYamlReader.readNamedIndexField(
    valueMap: MutableValueItems,
    nameDescriptor: FlexBytesDefinitionWrapper<String, String, IsPropertyContext, StringDefinition, DO>,
    indexDescriptor: FixedBytesDefinitionWrapper<UInt, *, IsPropertyContext, NumberDefinition<UInt>, DO>
) {
    if (currentToken != StartComplexFieldName || nextToken() !is StartObject) {
        throw IllegalJsonOperation("Expected named index like '? [0: name]'")
    }

    val index = (nextToken() as? FieldName)?.value?.toUInt()
        ?: throw IllegalJsonOperation("Expected index integer as field name like '? 0: name'")
    valueMap[indexDescriptor.index] = index

    (nextToken() as? Value<*>)
        ?: throw IllegalJsonOperation("Expected property name as value like '? 0: name'")
    valueMap[nameDescriptor.index] = nameDescriptor.readJson(this, null)

    if (nextToken() != EndObject || nextToken() != EndComplexFieldName) {
        throw IllegalJsonOperation("Expected only one index/value inside key like '? [0: name]' Start descriptor ': '  on a line below in same indent as '?'")
    }
    nextToken() // Move to next value
}

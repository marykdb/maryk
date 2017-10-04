package maryk.core.objects

import maryk.core.json.IllegalJsonOperation
import maryk.core.json.JsonGenerator
import maryk.core.json.JsonParser
import maryk.core.json.JsonToken
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import maryk.core.properties.exceptions.createPropertyValidationUmbrellaException
import maryk.core.properties.references.PropertyReference

class Def<T: Any, in DM: Any>(val propertyDefinition: IsPropertyDefinition<T>, val propertyGetter: (DM) -> T?)

/**
 * A Data Model for converting and validating DataObjects
 * @param <DO> Type of DataObject which is modeled
 */
abstract class DataModel<DO: Any>(
        val construct: (Map<Int, *>) -> DO,
        val definitions: List<Def<*, DO>>
) {
    protected val indexToDefinition: Map<Int, Def<*, DO>>
    private val nameToDefinition: Map<String, Def<*, DO>>

    init {
        indexToDefinition = mutableMapOf()
        nameToDefinition = mutableMapOf()

        definitions.forEach {
            val def = it.propertyDefinition
            assert(def.index in (0..Short.MAX_VALUE), { "${def.index} for ${def.name} is outside range $(0..Short.MAX_VALUE)" })
            assert(indexToDefinition[def.index] == null, { "Duplicate index ${def.index} for ${def.name} and ${indexToDefinition[def.index]?.propertyDefinition?.name}" })
            assert(def.name != null, {"Name of property should be set"})
            indexToDefinition[def.index] = it
            nameToDefinition[def.name!!] = it
        }
    }
    fun getDefinition(ofString: String) = nameToDefinition[ofString]?.propertyDefinition

    fun getDefinition(ofIndex: Int) = indexToDefinition[ofIndex]?.propertyDefinition
    fun getPropertyGetter(ofString: String) = nameToDefinition[ofString]?.propertyGetter

    fun getPropertyGetter(ofIndex: Int) = indexToDefinition[ofIndex]?.propertyGetter

    /** Validate a DataObject
     * @param dataObject to validate
     * @param parentRef  parent reference to the model
     * @throws PropertyValidationUmbrellaException if input was invalid
     */
    @Throws(PropertyValidationUmbrellaException::class)
    fun validate(dataObject: DO, parentRefFactory: () -> PropertyReference<*, *>? = { null }) {
        createPropertyValidationUmbrellaException(parentRefFactory) { addException ->
            definitions.forEach {
                @Suppress("UNCHECKED_CAST")
                val def: IsPropertyDefinition<Any> = it.propertyDefinition as IsPropertyDefinition<Any>
                try {
                    def.validate(
                            newValue = it.propertyGetter(dataObject),
                            parentRefFactory = parentRefFactory
                    )
                } catch (e: PropertyValidationException) {
                    addException(e)
                }
            }
        }
    }

    /** Validate a map of values
     * @param map with values to validate
     * @param parentRef  parent reference to the model
     * @throws PropertyValidationUmbrellaException if input was invalid
     */
    @Throws(PropertyValidationUmbrellaException::class)
    fun validate(map: Map<Int, Any>, parentRefFactory: () -> PropertyReference<*, *>? = { null }) {
        createPropertyValidationUmbrellaException(parentRefFactory) { addException ->
            map.forEach { (key, value) ->
                val definition = indexToDefinition[key] ?: return@forEach

                @Suppress("UNCHECKED_CAST")
                val def: IsPropertyDefinition<Any> = definition.propertyDefinition as IsPropertyDefinition<Any>
                try {
                    def.validate(
                            newValue = value,
                            parentRefFactory = parentRefFactory
                    )
                } catch (e: PropertyValidationException) {
                    addException(e)
                }
            }
        }
    }

    /** Convert an object to JSON
     * @param generator to generate JSON with
     * @param obj to convert to JSON
     */
    fun toJson(generator: JsonGenerator, obj: DO) {
        generator.writeStartObject()
        @Suppress("UNCHECKED_CAST")
        for (def in definitions as List<Def<Any, DO>>) {
            val name = def.propertyDefinition.name!!
            val value = def.propertyGetter(obj) ?: break

            generator.writeFieldName(name)

            def.propertyDefinition.writeJsonValue(generator, value)
        }
        generator.writeEndObject()
    }

    /** Convert a map with values to JSON
     * @param generator to generate JSON with
     * @param map with values to convert to JSON
     */
    fun toJson(generator: JsonGenerator, map: Map<Int, Any>) {
        generator.writeStartObject()
        for ((key, value) in map) {
            @Suppress("UNCHECKED_CAST")
            val def = indexToDefinition[key] as Def<Any, DO>? ?: break
            val name = def.propertyDefinition.name!!

            generator.writeFieldName(name)
            def.propertyDefinition.writeJsonValue(generator, value)
        }
        generator.writeEndObject()
    }

    /** Convert to a DataModel from JSON
     * @param parser to parse JSON with
     * @return map with all the values
     */
    fun fromJson(parser: JsonParser): Map<Int, Any> {
        if (parser.currentToken == JsonToken.START_JSON){
            parser.nextToken()
        }

        if (parser.currentToken != JsonToken.START_OBJECT) {
            throw IllegalJsonOperation("Expected object at start of json")
        }

        val valueMap: MutableMap<Int, Any> = mutableMapOf()
        parser.nextToken()
        walker@ do {
            val token = parser.currentToken
            when (token) {
                JsonToken.FIELD_NAME -> {
                    val definition = getDefinition(parser.lastValue)
                    if (definition == null) {
                        parser.skipUntilNextField()
                        continue@walker
                    } else {
                        parser.nextToken()

                        valueMap.put(
                                definition.index.toInt(),
                                definition.parseFromJson(parser)
                        )
                    }
                }
                else -> break@walker
            }
            parser.nextToken()
        } while (token !is JsonToken.STOPPED)

        return valueMap
    }

    /** Convert to a DataModel from JSON
     * @param parser to parse JSON with
     * @return DataObject represented by the JSON
     */
    fun fromJsonToObject(parser: JsonParser) = construct(this.fromJson(parser))
}

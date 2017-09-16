package maryk.core.objects

import maryk.core.json.JsonGenerator
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
abstract class DataModel<DO: Any>(val definitions: List<Def<*, DO>>) {
    protected val indexToDefinition: Map<Short, Def<*, DO>>
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
    fun getDefinition(ofString: String) = nameToDefinition.get(ofString)?.propertyDefinition

    fun getDefinition(ofIndex: Short) = indexToDefinition.get(ofIndex)?.propertyDefinition
    fun getPropertyGetter(ofString: String) = nameToDefinition.get(ofString)?.propertyGetter

    fun getPropertyGetter(ofIndex: Short) = indexToDefinition.get(ofIndex)?.propertyGetter

    /**
     * Validate a DataObject
     *
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
}

package maryk.core.models

import maryk.core.objects.ObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.DataModelContext
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

typealias SimpleObjectDataModel<DO, P> = AbstractObjectDataModel<DO, P, IsPropertyContext, IsPropertyContext>
typealias DefinitionDataModel<DO> = AbstractObjectDataModel<DO, ObjectPropertyDefinitions<DO>, DataModelContext, DataModelContext>
internal typealias QueryDataModel<DO, P> = AbstractObjectDataModel<DO, P, DataModelPropertyContext, DataModelPropertyContext>
internal typealias SimpleQueryDataModel<DO> = AbstractObjectDataModel<DO, ObjectPropertyDefinitions<DO>, DataModelPropertyContext, DataModelPropertyContext>

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model. [DO] is the type of DataObjects described by this model and [CX] the context to be used on the properties
 * to read and write. [CXI] is the input Context for properties. This can be different because the ObjectDataModel can create
 * its own context by transforming the given context.
 */
abstract class AbstractObjectDataModel<DO: Any, P: ObjectPropertyDefinitions<DO>, in CXI: IsPropertyContext, CX: IsPropertyContext> internal constructor(
    properties: P
) : IsObjectDataModel<DO, P>, AbstractDataModel<DO, P, ObjectValues<DO, P>, CXI, CX>(properties) {
    override fun validate(
        dataObject: DO,
        refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>, *>?
    ) {
        createValidationUmbrellaException(refGetter) { addException ->
            for (it in this.properties) {
                try {
                    it.validate(
                        newValue = getValueFromDefinition(it, dataObject, null),
                        parentRefFactory = refGetter
                    )
                } catch (e: ValidationException) {
                    addException(e)
                }
            }
        }
    }

    /**
     * Write an [obj] of this ObjectDataModel to JSON with [writer]
     * Optionally pass a [context] when needed for more complex property types
     */
    open fun writeJson(obj: DO, writer: IsJsonLikeWriter, context: CX? = null) {
        writer.writeStartObject()
        for (definition in this.properties) {
            val value = getValueFromDefinition(definition, obj, context) ?: continue

            definition.capture(context, value)

            writeJsonValue(definition, writer, value, context)
        }
        writer.writeEndObject()
    }

    /**
     * Calculates the byte length for [dataObject]
     * The [cacher] caches any values needed to write later.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    internal fun calculateProtoBufLength(dataObject: DO, cacher: WriteCacheWriter, context: CX? = null) : Int {
        var totalByteLength = 0
        for (definition in this.properties) {
            val value = getValueFromDefinition(definition, dataObject, context) ?: continue

            definition.capture(context, value)

            totalByteLength += definition.definition.calculateTransportByteLengthWithKey(definition.index, value, cacher, context)
        }
        return totalByteLength
    }

    /**
     * Write a ProtoBuf from a [dataObject] to [writer] and get
     * possible cached values from [cacheGetter]
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    internal fun writeProtoBuf(dataObject: DO, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null) {
        for (definition in this.properties) {
            val value = getValueFromDefinition(definition, dataObject, context) ?: continue

            definition.capture(context, value)

            definition.definition.writeTransportBytesWithKey(definition.index, value, cacheGetter, writer, context)
        }
    }

    protected open fun getValueFromDefinition(
        definition: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        obj: DO,
        context: CX?
    ) = definition.getPropertyAndSerialize(obj, context)

    /** Transform [context] into context specific to ObjectDataModel. Override for specific implementation */
    @Suppress("UNCHECKED_CAST")
    internal open fun transformContext(context: CXI?): CX?  = context as CX?
}

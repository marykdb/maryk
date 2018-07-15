package maryk.core.models

import maryk.core.objects.Values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

typealias SimpleDataModel<DM, P> = AbstractValuesDataModel<DM, P, IsPropertyContext>
typealias ValuesDataModelImpl<CX> = AbstractValuesDataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, CX>

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model of type [DM]. [CX] is the context to be used on the properties
 * to read and write. This can be different because the DataModel can create
 * its own context by transforming the given context.
 */
abstract class AbstractValuesDataModel<DM: IsValuesDataModel<P>, P: PropertyDefinitions, CX: IsPropertyContext> internal constructor(
    properties: P
) : IsTypedValuesDataModel<DM, P>, AbstractDataModel<Any, P, CX, CX>(properties) {

    override fun validate(
        map: Values<DM, P>,
        refGetter: () -> IsPropertyReference<Values<DM, P>, IsPropertyDefinition<Values<DM, P>>>?
    ) {
        createValidationUmbrellaException(refGetter) { addException ->
            for (key in map.keys) {
                val definition = properties.get(key) ?: continue
                val value = map<Any?>(key) ?: continue // skip empty values
                try {
                    definition.validate(
                        newValue = value,
                        parentRefFactory = refGetter
                    )
                } catch (e: ValidationException) {
                    addException(e)
                }
            }
        }
    }

    /**
     * Write an [map] with values for this ObjectDataModel to JSON with [writer]
     * Optionally pass a [context] when needed for more complex property types
     */
    open fun writeJson(map: Values<DM, P>, writer: IsJsonLikeWriter, context: CX? = null) {
        writer.writeStartObject()
        for (key in map.keys) {
            val value = map<Any?>(key) ?: continue // skip empty values

            val definition = properties.get(key) ?: continue

            definition.capture(context, value)

            writeJsonValue(definition, writer, value, context)
        }
        writer.writeEndObject()
    }

    /**
     * Read JSON from [reader] to a Map with values
     * Optionally pass a [context] when needed to read more complex property types
     */
    open fun readJson(reader: IsJsonLikeReader, context: CX? = null): Values<DM, P> {
        return this.map {
            this@AbstractValuesDataModel.readJsonToMap(reader, context)
        }
    }

    /**
     * Calculates the byte length for the DataObject contained in [map]
     * The [cacher] caches any values needed to write later.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    internal fun calculateProtoBufLength(map: Values<DM, P>, cacher: WriteCacheWriter, context: CX? = null) : Int {
        var totalByteLength = 0
        for (key in map.keys) {
            val value = map<Any?>(key) ?: continue // skip empty values

            val def = properties.get(key) ?: continue

            def.capture(context, value)

            totalByteLength += def.definition.calculateTransportByteLengthWithKey(def.index, value, cacher, context)
        }
        return totalByteLength
    }

    /**
     * Write a ProtoBuf from a [map] with values to [writer] and get
     * possible cached values from [cacheGetter]
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    internal fun writeProtoBuf(map: Values<DM, P>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null) {
        for (key in map.keys) {
            val value = map<Any?>(key) ?: continue // skip empty values

            val definition = properties.get(key) ?: continue

            definition.capture(context, value)

            definition.definition.writeTransportBytesWithKey(definition.index, value, cacheGetter, writer, context)
        }
    }

    /**
     * Read ProtoBuf bytes from [reader] until [length] to a Map of values
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    internal fun readProtoBuf(length: Int, reader: () -> Byte, context: CX? = null): Values<DM, P> {
        return this.map {
            this@AbstractValuesDataModel.readProtoBufToMap(length, reader, context)
        }
    }
}

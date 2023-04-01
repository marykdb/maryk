package maryk.core.models

import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

typealias SimpleObjectDataModel<DO, P> = AbstractObjectDataModel<DO, P, IsPropertyContext, IsPropertyContext>
typealias DefinitionDataModel<DO> = AbstractObjectDataModel<DO, ObjectPropertyDefinitions<DO>, ContainsDefinitionsContext, ContainsDefinitionsContext>
internal typealias QueryDataModel<DO, P> = AbstractObjectDataModel<DO, P, RequestContext, RequestContext>

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model. [DO] is the type of DataObjects described by this model and [CX] the context to be used on the properties
 * to read and write. [CXI] is the input Context for properties. This can be different because the ObjectDataModel can create
 * its own context by transforming the given context.
 */
abstract class AbstractObjectDataModel<DO : Any, P : IsObjectPropertyDefinitions<DO>, in CXI : IsPropertyContext, CX : IsPropertyContext> internal constructor(
    properties: P
) : IsDataModel<P>, AbstractDataModel<DO, P, ObjectValues<DO, P>, CXI, CX>(properties) {

    open fun writeJson(
        obj: DO,
        writer: IsJsonLikeWriter,
        context: CX? = null
    ) {
        this.writeJson(obj, writer, context, null)
    }

    /**
     * Write an [obj] of this ObjectDataModel to JSON with [writer]
     * Optionally pass a [context] when needed for more complex property types
     */
    fun writeJson(
        obj: DO,
        writer: IsJsonLikeWriter,
        context: CX? = null,
        skip: List<IsDefinitionWrapper<*, *, *, DO>>? = null
    ) {
        writer.writeStartObject()
        for (definition in this.properties) {
            if (skip != null && skip.contains(definition)) {
                continue
            }
            val value = getValueWithDefinition(definition, obj, context) ?: continue

            definition.capture(context, value)

            writeJsonValue(definition, writer, value, context)
        }
        writer.writeEndObject()
    }

    internal open fun getValueWithDefinition(
        definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        obj: DO,
        context: CX?
    ): Any? = if (obj is ObjectValues<*, *>) {
        obj.original(definition.index)
    } else {
        definition.getPropertyAndSerialize(obj, context)
    }

    /** Transform [context] into context specific to ObjectDataModel. Override for specific implementation */
    @Suppress("UNCHECKED_CAST")
    internal open fun transformContext(context: CXI?): CX? = context as CX?
}

package maryk.core.models.serializers

import maryk.core.models.ContextualDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

open class ObjectDataModelSerializer<DO: Any, DM: IsObjectDataModel<DO>, CXI:IsPropertyContext, CX: IsPropertyContext>(
    model: DM,
): DataModelSerializer<DO, ObjectValues<DO, DM>, DM, CX>(model), IsObjectDataModelSerializer<DO, DM, CXI, CX> {
    /**
     * Write an [obj] of this ObjectDataModel to JSON with [writer]
     * Optionally pass a [context] when needed for more complex property types
     */
    override fun writeObjectAsJson(
        obj: DO,
        writer: IsJsonLikeWriter,
        context: CX?,
        skip: List<IsDefinitionWrapper<*, *, *, DO>>?
    ) {
        writer.writeStartObject()
        for (definition in this.model) {
            if (skip != null && skip.contains(definition)) {
                continue
            }
            val value = getValueWithDefinition(definition, obj, context) ?: continue

            definition.capture(context, value)

            writeJsonValue(definition, writer, value, context)
        }
        writer.writeEndObject()
    }

    override fun calculateObjectProtoBufLength(dataObject: DO, cacher: WriteCacheWriter, context: CX?): Int {
        var totalByteLength = 0
        for (definition in this.model) {
            val value = getValueWithDefinition(definition, dataObject, context)

            totalByteLength += protoBufLengthToAddForField(value, definition, cacher, context)
        }

        if (context is RequestContext && this.model.isNotEmpty()) {
            context.closeInjectLevel(this.model)
        }

        return totalByteLength
    }

    override fun writeObjectProtoBuf(
        dataObject: DO,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?,
    ) {
        for (definition in this.model) {
            val value = getValueWithDefinition(definition, dataObject, context)

            this.writeProtoBufField(value, definition, cacheGetter, writer, context)
        }
    }

    override fun getValueWithDefinition(
        definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        obj: DO,
        context: CX?
    ): Any? = when (obj) {
        is ObjectValues<*, *> ->
            obj.original(definition.index)
        is ValueDataObjectWithValues ->
            obj.values.original(definition.index)
        else ->
            definition.getPropertyAndSerialize(obj, context)
    }

    @Suppress("UNCHECKED_CAST")
    override fun transformContext(context: CXI?): CX? =
        if (model is ContextualDataModel<*, *, *, *>) {
            (model as ContextualDataModel<*, *, CXI, CX>).contextTransformer(context)
        } else context as CX?
}

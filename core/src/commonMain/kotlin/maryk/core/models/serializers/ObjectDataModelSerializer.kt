package maryk.core.models.serializers

import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import kotlin.js.JsName

interface IsObjectDataModelSerializer<DO: Any, DM: IsObjectPropertyDefinitions<DO>, CXI:IsPropertyContext, CX: IsPropertyContext> : IsDataModelSerializer<ObjectValues<DO, DM>, DM, CX> {
    /**
     * Calculates the byte length for [dataObject]
     * The [cacher] caches any values needed to write later.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun calculateObjectProtoBufLength(dataObject: DO, cacher: WriteCacheWriter, context: CX? = null): Int

    /**
     * Write a ProtoBuf from a [dataObject] to [writer] and get
     * possible cached values from [cacheGetter]
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    @JsName("writeProtoBufWithObject")
    fun writeObjectProtoBuf(
        dataObject: DO,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX? = null
    )
}

open class ObjectDataModelSerializer<DO: Any, DM: IsObjectPropertyDefinitions<DO>, CXI:IsPropertyContext, CX: IsPropertyContext>(
    model: DM,
): DataModelSerializer<DO, ObjectValues<DO, DM>, DM, CX>(model), IsObjectDataModelSerializer<DO, DM, CXI, CX> {
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

    internal open fun getValueWithDefinition(
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

    /** Transform [context] into context specific to ObjectDataModel. Override for specific implementation */
    @Suppress("UNCHECKED_CAST")
    internal open fun transformContext(context: CXI?): CX? = context as CX?
}

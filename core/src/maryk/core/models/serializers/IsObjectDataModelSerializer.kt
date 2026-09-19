package maryk.core.models.serializers

import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

interface IsObjectDataModelSerializer<DO: Any, DM: IsObjectDataModel<DO>, in CXI: IsPropertyContext, CX: IsPropertyContext> :
    IsDataModelSerializer<ObjectValues<DO, DM>, DM, CX> {
    /**
     * Write an [obj] of this ObjectDataModel to JSON with [writer], skipping [skip] properties
     * Optionally pass a [context] when needed for more complex property types
     */
    fun writeObjectAsJson(
        obj: DO,
        writer: IsJsonLikeWriter,
        context: CX? = null,
        skip: List<IsDefinitionWrapper<*, *, *, DO>>? = null
    )

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
    fun writeObjectProtoBuf(
        dataObject: DO,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX? = null
    )

    /** Transform [context] into context specific to ObjectDataModel. Override for specific implementation */
    fun transformContext(context: CXI?): CX?

    /** Get value from object [obj] using [definition] and [context] */
    fun getValueWithDefinition(
        definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        obj: DO,
        context: CX?
    ): Any?
}

package maryk.datastore.cassandra.model

import com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto
import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.cassandra.CassandraDataStore
import maryk.datastore.cassandra.META_Maryk_Model_Definitions
import java.nio.ByteBuffer

fun CassandraDataStore.storeModelDefinition(
    dataModel: IsRootDataModel,
) {
    val insertStmt = insertInto(META_Maryk_Model_Definitions)
        .value("name", bindMarker())
        .value("version", bindMarker())
        .value("definition", bindMarker())
        .value("dependent_definition", bindMarker())
        .build()

    val context = DefinitionsConversionContext()

    val cache = WriteCache()
    val modelByteSize = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(dataModel as RootDataModel<*>, cache, context)
    val modelInBytes = ByteBuffer.allocateDirect(modelByteSize)
    RootDataModel.Model.Serializer.writeObjectProtoBuf(dataModel, cache, modelInBytes::put, context)

    @Suppress("UNCHECKED_CAST")
    val dependentDefinitions = Definitions(
        context.dataModels.values.map { it.invoke(Unit) }.filter { it !== dataModel } as List<MarykPrimitive>
    )
    val dependentsByteSize = Definitions.Serializer.calculateObjectProtoBufLength(dependentDefinitions, cache, context)
    val definitionInBytes = ByteBuffer.allocateDirect(dependentsByteSize)
    Definitions.Serializer.writeObjectProtoBuf(dependentDefinitions, cache, definitionInBytes::put, context)

    session.execute(
        insertStmt
            .setNamedValues(mapOf(
                "name" to dataModel.Meta.name,
                "version" to ByteBuffer.wrap(
                    dataModel.Meta.version.toByteArray()
                ),
                "definition" to modelInBytes.flip(),
                "dependent_definition" to definitionInBytes.flip(),
            ))
    )
}

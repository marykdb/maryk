package maryk.datastore.cassandra.model

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.type.DataTypes
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder
import maryk.datastore.cassandra.META_Maryk_Model_Definitions

fun createModelDefinitionTableIfNeeded(session: CqlSession) {
    val metaTable = SchemaBuilder.createTable(META_Maryk_Model_Definitions)
        .ifNotExists()
        .withPartitionKey("name", DataTypes.TEXT)
        .withColumn("version", DataTypes.BLOB)
        .withColumn("definition", DataTypes.BLOB)
        .withColumn("dependent_definitions", DataTypes.BLOB)
    session.execute(metaTable.build())
}

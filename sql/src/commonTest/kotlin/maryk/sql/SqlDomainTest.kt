package maryk.sql

import kotlinx.datetime.LocalTime
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.fixedBytes
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.time
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.properties.types.Bytes
import maryk.core.query.requests.add
import maryk.datastore.memory.InMemoryDataStore
import maryk.test.models.Option
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object SqlDomainModel : RootDataModel<SqlDomainModel>(
    keyDefinition = { SqlDomainModel.id.ref() },
    indexes = { listOf(SqlDomainModel.at.ref(), SqlDomainModel.payload.ref()) },
) {
    val id by number(1u, UInt64, final = true)
    val at by time(2u)
    val kind by enum(3u, enum = Option)
    val target by reference(4u, dataModel = { SqlTestModel })
    val approximate by number(5u, Float32)
    val payload by fixedBytes(6u, byteSize = 2)
}

class SqlDomainTest {
    @Test fun enumAndReferenceTextConversionsUseDefinitionsAndValidateParameters() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel, 2u to SqlDomainModel))
        try {
            val key = SqlTestModel.key(SqlTestModel.create { id with 2uL })
            store.execute(SqlDomainModel.add(SqlDomainModel.create {
                id with 1uL
                at with LocalTime(12, 0)
                kind with Option.V2
                target with key
                approximate with 2.5f
                payload with Bytes(byteArrayOf(1, 2))
            }))
            val sql = MarykSql.create(store, options = SqlOptions(allowTableScan = true))
            assertEquals(SqlValue.UInt64(1u), sql.query("SELECT id FROM SqlDomainModel WHERE kind = 'VERSION2' AND target = ?", listOf(SqlValue.Text(key.toString()))).rows.single()[0])
            val prepared = sql.prepare("SELECT id FROM SqlDomainModel WHERE kind = ?")
            assertEquals(1, sql.query("SELECT id FROM SqlDomainModel WHERE kind = ?", listOf(SqlValue.Enum("Option", 2u, "V2"))).rows.size)
            assertEquals(SqlErrorCode.PARAMETER, assertFailsWith<SqlException> { prepared.execute(listOf(SqlValue.Enum("Option", 1u, "V2"))) }.code)
            assertEquals(SqlErrorCode.BINDING, assertFailsWith<SqlException> { sql.prepare("SELECT id FROM SqlDomainModel WHERE kind = 'V2(1)'") }.code)
            // The stored precision is seconds. A subsecond comparison is still valid SQL.
            assertEquals(1, sql.query("SELECT id FROM SqlDomainModel WHERE at < TIME '12:00:00.001'").rows.size)
            assertEquals(1, sql.query("SELECT id FROM SqlDomainModel WHERE CAST(approximate AS FLOAT64) > CAST(2.0e0 AS FLOAT64)").rows.size)
            // Comparison values need not have the stored property's fixed width.
            assertEquals(1, sql.query("SELECT id FROM SqlDomainModel WHERE payload > CAST('AA' AS BINARY)").rows.size)
        } finally { store.close() }
    }
}

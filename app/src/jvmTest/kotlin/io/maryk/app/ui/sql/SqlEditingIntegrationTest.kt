package io.maryk.app.ui.sql

import kotlinx.coroutines.runBlocking
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.datastore.memory.InMemoryDataStore
import maryk.sql.MarykSql
import maryk.sql.SqlErrorCode
import maryk.sql.SqlException
import maryk.sql.SqlOptions
import maryk.sql.SqlType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object SqlEditingItem : RootDataModel<SqlEditingItem>() {
    val value by string(1u)
}

class SqlEditingIntegrationTest {
    @Test fun selfJoinCompletionsPassSqlBinding() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlEditingItem))
        try {
            val sql = MarykSql.create(store, options = SqlOptions(allowTableScan = true))
            val text = "SELECT val FROM SqlEditingItem a JOIN SqlEditingItem b ON a.__key = b.__key"
            assertEquals(SqlErrorCode.BINDING, assertFailsWith<SqlException> {
                sql.prepare(text.replace("SELECT val", "SELECT value"))
            }.code)
            val completions = sqlCompletions(text, "SELECT val".length, sql.catalog).filter { it.label == "value" }
            assertEquals(2, completions.size)
            for (completion in completions) {
                val completed = applySqlCompletion(text, completion).first
                assertEquals(SqlType.TEXT, sql.prepare(completed).columns.single().type, completed)
            }
        } finally {
            store.close()
        }
    }
}

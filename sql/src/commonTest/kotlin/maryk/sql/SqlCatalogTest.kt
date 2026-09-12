package maryk.sql

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.decimal
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.UInt64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal object SqlTestModel : RootDataModel<SqlTestModel>(keyDefinition = { SqlTestModel.id.ref() }) {
    val id by number(1u, UInt64, final = true)
    val category by string(2u, required = false)
    val amount by decimal(3u, scale = 2u, required = false)
    val active by boolean(4u, required = false)
    val note by string(5u, required = false, default = "default")
}

class SqlCatalogTest {
    @Test
    fun catalogExposesStoredTypesAndModelKey() {
        val table = SqlTable("items", SqlTestModel)
        assertEquals(listOf("__key", "id", "category", "amount", "active", "note"), table.columns.map { it.name })
        assertEquals(SqlType.UINT64, table.columns.single { it.name == "id" }.type)
        assertEquals(SqlType.DECIMAL, table.columns.single { it.name == "amount" }.type)
        assertEquals(false, table.columns.single { it.name == "id" }.nullable)
    }

    @Test
    fun catalogRejectsAmbiguousNamesAndAllowsExplicitColumnLists() {
        assertFailsWith<SqlException> { SqlCatalog(listOf(SqlTable("Items", SqlTestModel), SqlTable("items", SqlTestModel))) }
        val table = SqlTable("public_items", SqlTestModel, exposedColumns = setOf("id", "category"))
        assertEquals(listOf("id", "category"), table.columns.map { it.name })
        assertFailsWith<SqlException> { SqlTable("bad", SqlTestModel, exposedColumns = setOf("missing")) }
    }
}

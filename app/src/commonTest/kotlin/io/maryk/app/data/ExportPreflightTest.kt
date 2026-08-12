package io.maryk.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExportPreflightTest {
    @Test
    fun renderFailurePublishesNoFiles() {
        val published = mutableListOf<String>()

        assertFailsWith<IllegalArgumentException> {
            preflightAndPublish(
                values = listOf("first", "invalid"),
                render = { value ->
                    require(value != "invalid")
                    value
                },
                publish = { value, _ -> published += value },
            )
        }

        assertEquals(emptyList(), published)
    }
}

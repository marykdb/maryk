@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.processors.datastore

import maryk.test.models.ComplexMapModel

val complexValues = ComplexMapModel(
    stringString = mapOf("v1" to "a", "v22" to "b")
)

val complexValuesAsStorables = arrayOf(
    "0c" to 2,
    "0c7631" to "a",
    "0c763232" to "b"
)

package maryk.core.processors.datastore

import maryk.core.properties.types.TypedValue
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T3
import maryk.test.models.MarykTypeEnum.T4
import maryk.test.models.MarykTypeEnum.T5
import maryk.test.models.MarykTypeEnum.T6
import maryk.test.models.MarykTypeEnum.T7
import maryk.test.models.SimpleMarykTypeEnum.S3

val complexValues = ComplexModel(
    multi = TypedValue(T3, EmbeddedMarykModel("u3", EmbeddedMarykModel("ue3"))),
    mapStringString = mapOf("v1" to "a", "v22" to "b"),
    mapIntObject = mapOf(
        1u to EmbeddedMarykModel("t1"),
        2u to EmbeddedMarykModel("t2", EmbeddedMarykModel("te2"))
    ),
    mapIntMulti = mapOf(
        2u to TypedValue(T3, EmbeddedMarykModel("m3", EmbeddedMarykModel("me3"))),
        5u to TypedValue(T1, "TEST"),
        7u to TypedValue(T4, listOf("a", "b")),
        8u to TypedValue(T5, setOf("c", "d")),
        9u to TypedValue(T6, mapOf(
            5u to "e",
            6u to "f"
        )),
        10u to TypedValue(T7, TypedValue(S3, EmbeddedMarykModel("g")))
    ),
    mapWithList = mapOf(
        "a" to listOf("a1", "a2")
    ),
    mapWithSet = mapOf(
        "b" to setOf("b1", "b2")
    ),
    mapWithMap = mapOf(
        "c" to mapOf("c1" to "c2")
    )
)

val complexValuesAsStorables = arrayOf(
    "09" to T3,
    "091d" to Unit,
    "091d09" to "u3",
    "091d16" to Unit,
    "091d1609" to "ue3",
    "14" to 2,
    "14027631" to "a",
    "1403763232" to "b",
    "1c" to 2,
    "1c0400000001" to Unit,
    "1c040000000109" to "t1",
    "1c0400000002" to Unit,
    "1c040000000209" to "t2",
    "1c040000000216" to Unit,
    "1c04000000021609" to "te2",
    "24" to 6,
    "240400000002" to T3,
    "2404000000021d" to Unit,
    "2404000000021d09" to "m3",
    "2404000000021d16" to Unit,
    "2404000000021d1609" to "me3",
    "240400000005" to TypedValue(T1, "TEST"),
    "240400000007" to T4,
    "2404000000072502" to 2,
    "240400000007250200000000" to "a",
    "240400000007250200000001" to "b",
    "240400000008" to T5,
    "2404000000082d03" to 2,
    "2404000000082d030163" to "c",
    "2404000000082d030164" to "d",
    "240400000009" to T6,
    "2404000000093504" to 2,
    "24040000000935040400000005" to "e",
    "24040000000935040400000006" to "f",
    "24040000000a" to T7,
    "24040000000a3d" to S3,
    "24040000000a3d1d" to Unit,
    "24040000000a3d1d09" to "g",
    "2c" to 1,
    "2c016102" to 2,
    "2c01610200000000" to "a1",
    "2c01610200000001" to "a2",
    "34" to 1,
    "34016203" to 2,
    "34016203026231" to "b1",
    "34016203026232" to "b2",
    "3c" to 1,
    "3c016304" to 1,
    "3c016304026331" to "c2"
)

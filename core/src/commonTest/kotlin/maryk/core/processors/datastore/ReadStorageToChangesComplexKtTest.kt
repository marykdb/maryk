package maryk.core.processors.datastore

import maryk.core.properties.references.dsl.at
import maryk.core.properties.references.dsl.atType
import maryk.core.properties.references.dsl.refAt
import maryk.core.properties.references.dsl.refAtType
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.pairs.withType
import maryk.lib.extensions.initByteArrayByHex
import maryk.test.models.ComplexModel
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T3
import maryk.test.models.MarykTypeEnum.T4
import maryk.test.models.MarykTypeEnum.T5
import maryk.test.models.MarykTypeEnum.T6
import maryk.test.models.MarykTypeEnum.T7
import maryk.test.models.SimpleMarykTypeEnum.S3
import kotlin.test.Test
import kotlin.test.assertEquals

private val complexValuesAsStorablesWithVersion = arrayOf(
    "09" to arrayOf(1234uL to T3),
    "091d" to arrayOf(1234uL to Unit),
    "091d09" to arrayOf(1234uL to "u3"),
    "091d16" to arrayOf(1234uL to Unit),
    "091d1609" to arrayOf(1234uL to "ue3"),
    "14" to arrayOf(1234uL to 2),
    "14027631" to arrayOf(1234uL to "a"),
    "1403763232" to arrayOf(1234uL to "b"),
    "1c" to arrayOf(1234uL to 2),
    "1c0400000001" to arrayOf(1234uL to Unit),
    "1c040000000109" to arrayOf(1234uL to "t1"),
    "1c0400000002" to arrayOf(1234uL to Unit),
    "1c040000000209" to arrayOf(1234uL to "t2"),
    "1c040000000216" to arrayOf(1234uL to Unit),
    "1c04000000021609" to arrayOf(1234uL to "te2"),
    "24" to arrayOf(1234uL to 6),
    "240400000002" to arrayOf(1234uL to T3),
    "2404000000021d" to arrayOf(1234uL to Unit),
    "2404000000021d09" to arrayOf(1234uL to "m3"),
    "2404000000021d16" to arrayOf(1234uL to Unit),
    "2404000000021d1609" to arrayOf(1234uL to "me3"),
    "240400000005" to arrayOf(1234uL to TypedValue(T1, "TEST")),
    "240400000007" to arrayOf(1234uL to T4),
    "2404000000072502" to arrayOf(1234uL to 2),
    "240400000007250200000000" to arrayOf(1234uL to "a"),
    "240400000007250200000001" to arrayOf(1234uL to "b"),
    "240400000007250200000002" to arrayOf(1234uL to null),
    "240400000008" to arrayOf(1234uL to T5),
    "2404000000082d03" to arrayOf(1234uL to 2),
    "2404000000082d030163" to arrayOf(1234uL to "c"),
    "2404000000082d030164" to arrayOf(1234uL to "d"),
    "240400000009" to arrayOf(1234uL to T6),
    "2404000000093504" to arrayOf(1234uL to 2),
    "24040000000935040400000005" to arrayOf(1234uL to "e"),
    "24040000000935040400000006" to arrayOf(1234uL to "f"),
    "24040000000a" to arrayOf(1234uL to T7),
    "24040000000a3d" to arrayOf(1234uL to S3),
    "24040000000a3d1d" to arrayOf(1234uL to Unit),
    "24040000000a3d1d09" to arrayOf(1234uL to "g"),
    "2c" to arrayOf(1234uL to 1),
    "2c016102" to arrayOf(1234uL to 2),
    "2c01610200000000" to arrayOf(1234uL to "a1"),
    "2c01610200000001" to arrayOf(1234uL to "a2"),
    "2c01610200000002" to arrayOf(1234uL to null),
    "34" to arrayOf(1234uL to 1),
    "34016203" to arrayOf(1234uL to 2),
    "34016203026231" to arrayOf(1234uL to "b1"),
    "34016203026232" to arrayOf(1234uL to "b2"),
    "34016203026233" to arrayOf(1234uL to null),
    "3c" to arrayOf(1234uL to 1),
    "3c016304" to arrayOf(1234uL to 1),
    "3c016304026331" to arrayOf(1234uL to "c2")
)

class ReadStorageToChangesComplexKtTest {
    @Test
    fun convertStorageToChanges() {
        var qualifierIndex = -1
        val values = ComplexModel.readStorageToChanges(
            getQualifier = { resultHandler ->
                val qualifier = complexValuesAsStorablesWithVersion.getOrNull(++qualifierIndex)?.let {
                    initByteArrayByHex(it.first)
                }
                qualifier?.let { resultHandler({ qualifier[it] }, qualifier.size); true } ?: false
            },
            select = null,
            creationVersion = 1234uL,
            processValue = { _, _, changer ->
                complexValuesAsStorablesWithVersion[qualifierIndex].second.forEach {
                    changer(it.first, it.second)
                }
            }
        )

        assertEquals(
            listOf(
                VersionedChanges(
                    1234UL,
                    listOf(
                        ObjectCreate,
                        MultiTypeChange(
                            ComplexModel { multi::ref } withType T3,
                            ComplexModel { mapIntMulti.refAt(2u) } withType T3,
                            ComplexModel { mapIntMulti.refAt(7u) } withType T4,
                            ComplexModel { mapIntMulti.refAt(8u) } withType T5,
                            ComplexModel { mapIntMulti.refAt(9u) } withType T6,
                            ComplexModel { mapIntMulti.refAt(10u) } withType T7
                        ),
                        Change(
                            ComplexModel { multi.withType(T3) { value::ref } } with "u3",
                            ComplexModel { multi.withType(T3) { model { value::ref } } } with "ue3",
                            ComplexModel { mapStringString.refAt("v1") } with "a",
                            ComplexModel { mapStringString.refAt("v22") } with "b",
                            ComplexModel { mapIntObject.refAt(1u) } with Unit,
                            ComplexModel { mapIntObject.at(1u) { value::ref } } with "t1",
                            ComplexModel { mapIntObject.refAt(2u) } with Unit,
                            ComplexModel { mapIntObject.at(2u) { value::ref } } with "t2",
                            ComplexModel { mapIntObject.at(2u) { model { value::ref } } } with "te2",
                            ComplexModel { mapIntMulti.at(2u) { atType(T3) { value::ref } } } with "m3",
                            ComplexModel { mapIntMulti.at(2u) { atType(T3) { model { value::ref } } } } with "me3",
                            ComplexModel { mapIntMulti.refAt(5u) } with TypedValue(T1, "TEST"),
                            ComplexModel { mapIntMulti.at(7u) { atType(T4) { refAt(0u) } } } with "a",
                            ComplexModel { mapIntMulti.at(7u) { atType(T4) { refAt(1u) } } } with "b",
                            ComplexModel { mapIntMulti.at(7u) { atType(T4) { refAt(2u) } } } with null,
                            ComplexModel { mapIntMulti.at(9u) { atType(T6) { refAt(5u) } } } with "e",
                            ComplexModel { mapIntMulti.at(9u) { atType(T6) { refAt(6u) }  } } with "f",
                            ComplexModel { mapIntMulti.at(10u) { atType(T7) { atType(S3) { value::ref } } } } with "g",
                            ComplexModel { mapWithList.at("a") { refAt(0u) } } with "a1",
                            ComplexModel { mapWithList.at("a") { refAt(1u) } } with "a2",
                            ComplexModel { mapWithList.at("a") { refAt(2u) } } with null,
                            ComplexModel { mapWithSet.at("b") { refAt("b3") } } with null,
                            ComplexModel { mapWithMap.at("c") { refAt("c1")  }} with "c2",
                        ),
                        SetChange(
                            ComplexModel { mapIntMulti.at(8u) { refAtType(T5) } }.change(
                                addValues = setOf("c", "d")
                            ),
                            ComplexModel { mapWithSet.refAt("b") }.change(
                                addValues = setOf("b1", "b2")
                            )
                        )
                    )
                )
            ),
            values
        )
    }
}

package maryk.core.aggregations.bucket

import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.Sum
import maryk.core.aggregations.metric.SumResponse
import maryk.core.properties.types.TypedValue
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.SimpleMarykTypeEnum.S3
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class TypesAggregatorTest {
    @Test
    fun aggregate() {
        val typesAggregator = Types(
            TestMarykModel { multi.refToType() },
            aggregations = Aggregations(
                "totalInt" to Sum(TestMarykModel { int::ref })
            )
        ).createAggregator()

        expect(
            TypesResponse(
                TestMarykModel { multi.refToType() }
            )
        ) {
            typesAggregator.toResponse()
        }

        typesAggregator.aggregate(
            createAggregator(
                TestMarykModel.run {
                    create(
                        multi with TypedValue(S1, "value 1"),
                        int with 2324
                    )
                }
            )
        )
        typesAggregator.aggregate(
            createAggregator(
                TestMarykModel.run {
                    create(
                        multi with TypedValue(S1, "value 2"),
                        int with 872364
                    )
                }
            )
        )

        typesAggregator.aggregate(
            createAggregator(
                TestMarykModel.run {
                    create(
                        multi with TypedValue(S3, EmbeddedMarykModel("E1"))
                    )
                }
            )
        )
        typesAggregator.aggregate(
            createAggregator(
                TestMarykModel.run {
                    create(
                        multi with TypedValue(S3, EmbeddedMarykModel("E1"))
                    )
                }
            )
        )
        typesAggregator.aggregate(
            createAggregator(
                TestMarykModel.run {
                    create(
                        multi with TypedValue(S3, EmbeddedMarykModel("E1"))
                    )
                }
            )
        )

        expect(
            TypesResponse(
                TestMarykModel { multi.refToType() },
                listOf(
                    Bucket(
                        S1,
                        AggregationsResponse(
                            mapOf(
                                "totalInt" to SumResponse(
                                    TestMarykModel { int::ref },
                                    874688
                                )
                            )
                        ),
                        2uL
                    ),
                    Bucket(
                        S3,
                        AggregationsResponse(
                            "totalInt" to SumResponse(
                                TestMarykModel { int::ref },
                                null
                            )
                        ),
                        3uL
                    )
                )
            )
        ) {
            typesAggregator.toResponse()
        }
    }
}

package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationsResponse
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.SimpleMarykTypeEnum.S3
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class TypesAggregatorTest {
    @Test
    fun aggregate() {
        val typesAggregator = TypesAggregator(
            Types(TestMarykModel { multi.refToType() })
        )

        expect(
            TypesResponse(
                TestMarykModel { multi.refToType() }
            )
        ) {
            typesAggregator.toResponse()
        }

        typesAggregator.aggregate(S1)
        typesAggregator.aggregate(S1)

        typesAggregator.aggregate(S3)
        typesAggregator.aggregate(S3)
        typesAggregator.aggregate(S3)

        expect(
            TypesResponse(
                TestMarykModel { multi.refToType() },
                listOf(
                    Bucket(
                        S1,
                        AggregationsResponse(
                            mapOf()
                        ),
                        2uL
                    ),
                    Bucket(
                        S3,
                        AggregationsResponse(
                            mapOf()
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

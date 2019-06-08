package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationsResponse
import maryk.test.models.Option.V1
import maryk.test.models.Option.V3
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class EnumValuesAggregatorTest {
    @Test
    fun aggregate() {
        val enumValuesAggregator = EnumValuesAggregator(
            EnumValues(TestMarykModel { enum::ref })
        )

        expect(
            EnumValuesResponse(
                TestMarykModel { enum::ref }
            )
        ) {
            enumValuesAggregator.toResponse()
        }

        enumValuesAggregator.aggregate(V1)
        enumValuesAggregator.aggregate(V1)

        enumValuesAggregator.aggregate(V3)
        enumValuesAggregator.aggregate(V3)
        enumValuesAggregator.aggregate(V3)

        expect(
            EnumValuesResponse(
                TestMarykModel { enum::ref },
                listOf(
                    Bucket(
                        V1,
                        AggregationsResponse(
                            mapOf()
                        ),
                        2uL
                    ),
                    Bucket(
                        V3,
                        AggregationsResponse(
                            mapOf()
                        ),
                        3uL
                    )
                )
            )
        ) {
            enumValuesAggregator.toResponse()
        }
    }
}

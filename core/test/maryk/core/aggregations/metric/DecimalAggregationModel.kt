package maryk.core.aggregations.metric

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.decimal
import maryk.core.properties.definitions.fixedBytes

internal object DecimalAggregationModel : RootDataModel<DecimalAggregationModel>() {
    val amount by decimal(
        index = 1u,
        scale = 2u,
    )
}

internal object NonArithmeticAggregationModel : RootDataModel<NonArithmeticAggregationModel>() {
    val bytes by fixedBytes(
        index = 1u,
        byteSize = 4,
    )
}

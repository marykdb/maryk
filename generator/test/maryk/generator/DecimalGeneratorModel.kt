package maryk.generator

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.decimal
import maryk.core.properties.types.Decimal

object DecimalGeneratorModel : RootDataModel<DecimalGeneratorModel>() {
    val amount by decimal(
        index = 1u,
        scale = 2u,
        byteSize = 2,
        default = Decimal.parse("12.30"),
    )
}

package maryk.core.properties.exceptions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test

class ValidationExceptionTest {
    private val ref = SimpleMarykModel { value::ref }

    private val validationUmbrellaException = ValidationUmbrellaException(
        null, listOf(
            AlreadyExistsException(ref, Key<IsRootDataModel<SimpleMarykModel>>(ByteArray(16))),
            AlreadySetException(ref),
            InvalidSizeException(ref, "wrong", 1u, 3u),
            InvalidValueException(ref, "wrong"),
            OutOfRangeException(ref, "wrong", "a", "g"),
            RequiredException(ref),
            ValidationUmbrellaException(
                ref,
                listOf(
                    NotEnoughItemsException(ref, 2u, 3u),
                    TooManyItemsException(ref, 10u, 3u)
                )
            )
        )
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
        ),
        dataModel = SimpleMarykModel.Model
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.validationUmbrellaException, ValidationUmbrellaException, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.validationUmbrellaException, ValidationUmbrellaException, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.validationUmbrellaException, ValidationUmbrellaException, { this.context })
    }
}

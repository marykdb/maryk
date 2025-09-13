package maryk.test.requests

import maryk.core.query.requests.add
import maryk.test.models.SimpleMarykModel

val addRequest = SimpleMarykModel.add(
    SimpleMarykModel.create { value with "haha1" },
    SimpleMarykModel.create { value with "haha2" }
)

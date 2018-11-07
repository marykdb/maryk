package maryk.test.requests

import maryk.core.query.requests.add
import maryk.test.models.SimpleMarykModel

val addRequest = SimpleMarykModel.add(
    SimpleMarykModel(value = "haha1"),
    SimpleMarykModel(value = "haha2")
)

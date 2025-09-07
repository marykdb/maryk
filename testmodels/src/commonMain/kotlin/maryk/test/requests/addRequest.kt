package maryk.test.requests

import maryk.core.query.requests.add
import maryk.test.models.SimpleMarykModel

val addRequest = SimpleMarykModel.add(
    SimpleMarykModel.create { value += "haha1" },
    SimpleMarykModel.create { value += "haha2" }
)

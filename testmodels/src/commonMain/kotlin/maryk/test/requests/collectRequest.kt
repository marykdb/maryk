package maryk.test.requests

import maryk.core.query.requests.CollectRequest
import maryk.core.query.requests.get
import maryk.test.models.SimpleMarykModel

val collectRequest = CollectRequest(
    "testName",
    SimpleMarykModel.get(
        SimpleMarykModel.key("dR9gVdRcSPw2molM1AiOng"),
        SimpleMarykModel.key("Vc4WgX/mQHYCSEoLtfLSUQ")
    )
)

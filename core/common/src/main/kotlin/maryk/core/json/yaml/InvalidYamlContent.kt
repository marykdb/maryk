package maryk.core.json.yaml

import maryk.core.json.InvalidJsonContent

/** Exception for invalid Yaml */
class InvalidYamlContent internal constructor(
    description: String
): InvalidJsonContent(description)

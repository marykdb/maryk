package maryk.yaml

import maryk.json.InvalidJsonContent

/** Exception for invalid Yaml */
class InvalidYamlContent internal constructor(
    description: String
): InvalidJsonContent(description)

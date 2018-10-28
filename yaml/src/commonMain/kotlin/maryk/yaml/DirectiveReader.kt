package maryk.yaml

import maryk.json.JsonToken
import maryk.lib.extensions.isLineBreak

private val yamlRegEx = Regex("^YAML ([0-9]).([0-9]+)$")
private val tagRegEx = Regex("^TAG (!|!!|![a-zAZ]+!) ([^ ]+)$")

/**
 * Reads YAML directives and fires [onDone] when done
 */
internal fun YamlCharReader.directiveReader(onDone: () -> JsonToken): JsonToken {
    var foundDirective = ""
    while(!this.lastChar.isLineBreak()) {
        foundDirective += lastChar
        read()
    }
    foundDirective = foundDirective.trimEnd()

    yamlRegEx.matchEntire(foundDirective)?.let {
        it.groups.let {
            if (this.yamlReader.version != null) {
                throw InvalidYamlContent("Cannot declare yaml version twice")
            }
            if (it[1]?.value != "1") {
                throw InvalidYamlContent("Unsupported Yaml major version")
            }
            this.yamlReader.version = "${it[1]?.value}.${it[2]?.value}"
        }
    }

    tagRegEx.matchEntire(foundDirective)?.let {
        it.groups.let {
            // Match should always contain 2 values
            if (it[1]!!.value in this.yamlReader.tags.keys) {
                throw InvalidYamlContent("Tag ${it[1]?.value} is already defined")
            }
            this.yamlReader.tags[it[1]!!.value] = it[2]!!.value
        }
    }

    return onDone()
}

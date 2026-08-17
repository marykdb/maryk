package maryk.yaml

import maryk.json.JsonToken
import maryk.lib.extensions.isLineBreak

private val yamlRegEx = Regex("^YAML ([0-9]).([0-9]+)$")
private val tagRegEx = Regex("^TAG (!|!!|![a-zAZ]+!) ([^ ]+)$")
private const val MAX_YAML_DIRECTIVE_LENGTH = 4096

/**
 * Reads YAML directives and fires [onDone] when done
 */
internal fun IsYamlCharReader.directiveReader(onDone: () -> JsonToken): JsonToken {
    val directive = StringBuilder()
    while (!this.lastChar.isLineBreak()) {
        directive.append(lastChar)
        if (directive.length > MAX_YAML_DIRECTIVE_LENGTH) {
            throw InvalidYamlContent(
                "Directive length budget exceeded: ${directive.length} > $MAX_YAML_DIRECTIVE_LENGTH"
            )
        }
        read()
    }
    val foundDirective = directive.toString().trimEnd()

    yamlRegEx.matchEntire(foundDirective)?.let {
        it.groups.let { match ->
            if (this.yamlReader.version != null) {
                throw InvalidYamlContent("Cannot declare yaml version twice")
            }
            if (match[1]?.value != "1") {
                throw InvalidYamlContent("Unsupported Yaml major version")
            }
            this.yamlReader.version = "${match[1]?.value}.${match[2]?.value}"
        }
    }

    tagRegEx.matchEntire(foundDirective)?.let {
        it.groups.let { match ->
            // Match should always contain 2 values
            if (match[1]!!.value in this.yamlReader.tags.keys) {
                throw InvalidYamlContent("Tag ${match[1]?.value} is already defined")
            }
            this.yamlReader.tags[match[1]!!.value] = match[2]!!.value
        }
    }

    return onDone()
}

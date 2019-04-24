package maryk.yaml

import maryk.json.JsonToken
import maryk.json.JsonToken.FieldName
import maryk.json.TokenType

/** Yaml Character reader to read until token and handle reader interrupts */
internal interface IsYamlCharReader: IsInternalYamlReader {
    val yamlReader: YamlReaderImpl

    /** Reads Yaml until next found Token */
    fun readUntilToken(extraIndent: Int, tag: TokenType? = null): JsonToken

    /** Handles reader interruptions */
    fun handleReaderInterrupt(): JsonToken
}

/** Yaml Character reader which uses the state in YamlReader to read until next token */
internal abstract class YamlCharReader(
    override val yamlReader: YamlReaderImpl
) : IsInternalYamlReader by yamlReader, IsYamlCharReader

/** Yaml Character reader which is a child to a parent reader */
internal abstract class YamlCharWithParentReader<out P : IsYamlCharReader>(
    yamlReader: YamlReaderImpl,
    val parentReader: P
) : YamlCharReader(yamlReader)

/** Yaml char reader which is aware of indentation */
internal interface IsYamlCharWithIndentsReader: IsInternalYamlReader, IsYamlCharReader {
    /** Indent count for this object */
    fun indentCount(): Int

    /** Continue on same indent level with this reader */
    fun continueIndentLevel(extraIndent: Int, tag: TokenType?): JsonToken

    /** Go back to a higher indent level of [indentCount] by closing this reader and passing optionally a [tokenToReturn] */
    fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken

    /** Signal reader a map key was found so this indent level expects maps */
    fun foundMap(tag: TokenType?, startedAtIndent: Int): JsonToken?

    /** Checks if field name was set and creates it or otherwise throws error */
    fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean): FieldName
}

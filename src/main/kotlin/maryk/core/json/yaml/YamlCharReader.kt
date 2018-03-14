package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/** Yaml Character reader which uses the state in YamlReader to read until next token */
internal abstract class YamlCharReader(
    internal val yamlReader: YamlReaderImpl
) : IsYamlReader by yamlReader {
    /** Reads Yaml until next found Token */
    abstract fun readUntilToken(): JsonToken
    /** Handles reader interuptions */
    abstract fun handleReaderInterrupt(): JsonToken
}

/** Yaml Character reader which is a child to a parent reader */
internal abstract class YamlCharWithParentReader<out P>(
    yamlReader: YamlReaderImpl,
    val parentReader: P
) : YamlCharReader(yamlReader)
        where P : IsYamlCharWithChildrenReader,
              P : YamlCharReader

/** Yaml char reader which is aware of indentation */
internal interface IsYamlCharWithIndentsReader {
    /** Indent count for this object */
    fun indentCount(): Int

    /** Indent count from perspective of children. Sometimes they need to indent a bit more */
    fun indentCountForChildren(): Int

    /** Continue on same indent level with this reader */
    fun continueIndentLevel(tag: TokenType?): JsonToken

    /** Continue on a deeper indent level below this reader with [parentReader] */
    fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : maryk.core.json.yaml.YamlCharReader,
                  P : maryk.core.json.yaml.IsYamlCharWithChildrenReader,
                  P : maryk.core.json.yaml.IsYamlCharWithIndentsReader

    /** Go back to a higher indent level of [indentCount] by closing this reader ans passing optionally a [tokenToReturn] */
    fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?): JsonToken

    /** Signal reader a map key was found so this indent level expects maps */
    fun foundMapKey(isExplicitMap: Boolean): JsonToken?
}

/** An interface for a Yaml char reader with children so children can call it when it is done*/
internal interface IsYamlCharWithChildrenReader {
    fun childIsDoneReading()
    fun isWithinMap(): Boolean
}

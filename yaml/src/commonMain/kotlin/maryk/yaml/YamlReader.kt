package maryk.yaml

import kotlinx.datetime.LocalDateTime
import maryk.json.ArrayType
import maryk.json.ExceptionWhileReadingJson
import maryk.json.IsJsonLikeReader
import maryk.json.JsonToken
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.MergeFieldName
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartComplexFieldName
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Stopped
import maryk.json.JsonWriteException
import maryk.json.MapType
import maryk.json.TokenType
import maryk.json.ValueType
import maryk.lib.extensions.isLineBreak

/** Unknown tag name to reader, pass allowUnknownTags true in YamlReader to get them */
class UnknownYamlTag(val name: String) : MapType, ValueType<Nothing>, ArrayType

@Suppress("FunctionName")
fun YamlReader(
    defaultTag: String? = null,
    tagMap: Map<String, Map<String, TokenType>>? = null,
    allowUnknownTags: Boolean = false,
    reader: () -> Char
): IsYamlReader =
    YamlReaderImpl(defaultTag, tagMap, allowUnknownTags, reader)

/** Interface to determine object is a yaml reader */
interface IsYamlReader : IsJsonLikeReader {
    /** Add token to stack to return first */
    fun pushToken(token: JsonToken)
}

/** Internal interface for the Yaml Reader functionality */
internal interface IsInternalYamlReader {
    /** Is last character which was read */
    val lastChar: Char
    /** Holds the current char reader */
    var currentReader: IsYamlCharReader

    /** Reads next Char */
    fun read()
}

internal interface YamlValueType<out T : Any> : ValueType<T> {
    object Binary : YamlValueType<ByteArray>
    object Merge : YamlValueType<Nothing>
    object TimeStamp : YamlValueType<LocalDateTime>
    object Value : YamlValueType<Nothing> //Default value
    object Yaml : YamlValueType<Nothing>
}

private val yamlTagMap = mapOf(
    "tag:yaml.org,2002:" to mapOf(
        "str" to ValueType.String,
        "bool" to ValueType.Bool,
        "null" to ValueType.Null,
        "float" to ValueType.Float,
        "int" to ValueType.Int,
        "yaml" to YamlValueType.Yaml,
        "value" to YamlValueType.Value,
        "merge" to YamlValueType.Merge,
        "binary" to YamlValueType.Binary,
        "timestamp" to YamlValueType.TimeStamp,
        "seq" to ArrayType.Sequence,
        "set" to ArrayType.Set,
        "map" to MapType.Map,
        "omap" to MapType.OrderedMap,
        "pairs" to MapType.Pairs
    )
)

/** Reads YAML from the supplied [reader] */
internal class YamlReaderImpl(
    private val defaultTag: String?,
    tagMap: Map<String, Map<String, TokenType>>?,
    private val allowUnknownTags: Boolean,
    private val reader: () -> Char
) : IsJsonLikeReader, IsInternalYamlReader, IsYamlReader {
    var version: String? = null

    override var currentToken: JsonToken = StartDocument

    override var lastChar: Char = '\u0000'
    override var currentReader: IsYamlCharReader = DocumentReader(this)

    private var unclaimedIndenting: Int? = null
    internal var hasException: Boolean = false
    internal val tags: MutableMap<String, String> = mutableMapOf()

    private val anchorReaders = mutableListOf<AnchorRecorder>()
    private val anchorReadersToRemove = mutableListOf<AnchorRecorder>()

    private val tokenStack = mutableListOf<JsonToken>()
    private val storedAnchors = mutableMapOf<String, Array<JsonToken>>()

    private var tokenDepth = 0
    private var merges = mutableListOf<Merge>()

    override var columnNumber = -1
    override var lineNumber = 1

    private val tagMap: Map<String, Map<String, TokenType>> = tagMap?.let {
        if (defaultTag != null && !tagMap.contains(defaultTag)) {
            throw JsonWriteException("Default tag should be defined in tag map")
        }
        yamlTagMap.plus(tagMap)
    } ?: yamlTagMap

    override fun nextToken(): JsonToken {
        try {
            this.currentToken = try {
                if (this.tokenStack.isNotEmpty()) {
                    this.tokenStack.removeAt(0)
                } else if (this.hasException) {
                    this.currentReader.handleReaderInterrupt()
                } else {
                    val reader = this.currentReader
                    if (this.unclaimedIndenting != null && reader is IsYamlCharWithIndentsReader) {
                        // Skip stray comments and read until first relevant character
                        if (this.lastChar == '#') {
                            while (!this.lastChar.isLineBreak()) {
                                read()
                            }
                            this.unclaimedIndenting = skipEmptyLinesAndCommentsAndCountIndents()
                        }

                        val remainder = reader.indentCount() - this.unclaimedIndenting!!
                        when {
                            remainder > 0 -> reader.endIndentLevel(this.unclaimedIndenting!!, null, null)
                            remainder == 0 -> {
                                this.unclaimedIndenting = null
                                reader.continueIndentLevel(0, null)
                            }
                            else -> // Indents are only left over on closing indents so should never be lower
                                throw InvalidYamlContent("Lower indent found than previous started indents")
                        }
                    } else {
                        reader.readUntilToken(0)
                    }
                }
            } catch (e: ExceptionWhileReadingJson) {
                this.hasException = true
                currentReader.handleReaderInterrupt()
            }

            when (currentToken) {
                is StartObject, is StartArray -> this.tokenDepth++
                is EndObject, is EndArray -> this.tokenDepth--
                is MergeFieldName -> {
                    this.merges.add(Merge(this.tokenDepth))
                    return this.nextToken()
                }
                else -> {
                    // Just continue
                }
            }

            // Handle map merges
            this.merges.lastOrNull()?.let { merge ->
                when (merge.isWithArray) {
                    null -> {
                        merge.setStartToken(this.currentToken)
                        return this.nextToken()
                    }
                    true -> {
                        if (merge.tokenStartDepth == this.tokenDepth) {
                            this.merges.remove(merge)
                        } else {
                            this.merges.add(
                                Merge(
                                    this.tokenDepth - 1,
                                    this.currentToken
                                )
                            )
                        }
                        return this.nextToken()
                    }
                    false -> {
                        if (merge.tokenStartDepth == this.tokenDepth) {
                            this.merges.remove(merge)
                            return this.nextToken()
                        }
                    }
                }
            }

            for (it in this.anchorReaders) {
                it.recordToken(currentToken, this.tokenDepth) { anchor, tokens ->
                    this.storedAnchors[anchor.trim()] = tokens
                    this.anchorReadersToRemove.add(it)
                }
            }

            for (it in this.anchorReadersToRemove) {
                this.anchorReaders.remove(it)
            }
            this.anchorReadersToRemove.clear()

            return currentToken
        } catch (e: InvalidYamlContent) {
            e.lineNumber = this.lineNumber
            e.columnNumber = this.columnNumber
            throw e
        }
    }

    internal fun skipEmptyLinesAndCommentsAndCountIndents(): Int {
        var currentIndentCount = 0
        while (this.lastChar.isWhitespace()) {
            if (this.lastChar.isLineBreak()) {
                currentIndentCount = 0
            } else {
                currentIndentCount++
            }
            read()

            if (this.lastChar == '#' && currentIndentCount != 0) {
                while (!this.lastChar.isLineBreak()) {
                    read()
                }
            }
        }
        return currentIndentCount
    }

    override fun skipUntilNextField(handleSkipToken: ((JsonToken) -> Unit)?) {
        val startDepth = this.tokenDepth
        nextToken()
        while (
            // Continue while there is not a field name on current stack depth or object has ended at below stack depth
            !(((currentToken is FieldName || currentToken is StartComplexFieldName) && this.tokenDepth <= startDepth) || (currentToken is EndObject && this.tokenDepth < startDepth))
            && currentToken !is Stopped
        ) {
            handleSkipToken?.invoke(this.currentToken)
            nextToken()
        }
    }

    override fun read() = try {
        if (lastChar.isLineBreak()) {
            lineNumber += 1
            columnNumber = 0
        } else {
            columnNumber += 1
        }
        lastChar = reader()
    } catch (e: Throwable) { // Reached end or something bad happened
        throw ExceptionWhileReadingJson()
    }

    fun setUnclaimedIndenting(indentCount: Int?) {
        this.unclaimedIndenting = indentCount
    }

    fun hasUnclaimedIndenting() = this.unclaimedIndenting != null

    fun resolveTag(prefix: String, tag: String): TokenType {
        return when {
            prefix == "!" && tag.startsWith('<') && tag.endsWith('>') -> {
                val realTag = tag.removeSurrounding("<", ">")
                if (!realTag.contains(':')) {
                    throw InvalidYamlContent("Invalid tag $tag")
                }

                val indexOfColon = realTag.lastIndexOf(':') + 1

                this.tagMap[
                        realTag.substring(0, indexOfColon)
                ]?.get(realTag.substring(indexOfColon))
                    ?: throw InvalidYamlContent("Unknown $tag")
            }
            this.tags.containsKey(prefix) -> {
                val resolvedPrefix = this.tags[prefix]!!

                if (resolvedPrefix.startsWith("!")) {
                    return this.resolveTag(
                        "!",
                        resolvedPrefix.removePrefix("!") + tag
                    )
                }

                this.tagMap[resolvedPrefix]?.get(tag)
                    ?: throw InvalidYamlContent("Unknown tag $resolvedPrefix$tag")
            }
            prefix == "!" && !this.defaultTag.isNullOrEmpty() -> {
                this.tagMap[this.defaultTag]?.get(tag)
                    ?: if (this.allowUnknownTags) {
                        UnknownYamlTag(tag)
                    } else throw InvalidYamlContent("Unknown tag $prefix$tag")
            }
            prefix == "!!" -> {
                this.tagMap["tag:yaml.org,2002:"]?.get(tag)
                    ?: throw InvalidYamlContent("Unknown tag $prefix$tag")
            }
            else -> throw InvalidYamlContent("Unknown tag prefix $prefix")
        }
    }

    override fun pushToken(token: JsonToken) {
        this.tokenStack.add(token)
    }

    fun pushTokenAsFirst(token: JsonToken) {
        this.tokenStack.add(0, token)
    }

    fun getTokensForAlias(alias: String): Array<JsonToken> {
        val trimmedAlias = alias.trim()
        if (trimmedAlias.isEmpty()) {
            throw InvalidYamlContent("Alias (*) does not contain valid name")
        }

        return this.storedAnchors[trimmedAlias] ?: throw InvalidYamlContent("Unknown alias *$trimmedAlias")
    }

    fun recordAnchors(anchorReader: AnchorRecorder) {
        anchorReader.setTokenStartDepth(this.tokenDepth)
        this.anchorReaders.add(anchorReader)
    }
}

private class Merge(
    val tokenStartDepth: Int,
    startToken: JsonToken? = null
) {
    var isWithArray: Boolean? = null

    init {
        startToken?.let {
            this.setStartToken(it)
        }
    }

    fun setStartToken(token: JsonToken) {
        when (token) {
            is StartArray -> this.isWithArray = true
            is StartObject -> this.isWithArray = false
            else -> throw InvalidYamlContent("Merges should contain Maps or Sequences with maps")
        }
    }
}

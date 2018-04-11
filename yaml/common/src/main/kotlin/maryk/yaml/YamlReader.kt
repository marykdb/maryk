package maryk.yaml

import maryk.json.ArrayType
import maryk.json.ExceptionWhileReadingJson
import maryk.json.IsJsonLikeReader
import maryk.json.JsonToken
import maryk.json.MapType
import maryk.json.TokenType
import maryk.json.ValueType
import maryk.lib.extensions.isLineBreak
import maryk.lib.time.DateTime

@Suppress("FunctionName")
fun YamlReader(
    defaultTag: String? = null,
    tagMap: Map<String, Map<String, TokenType>>? = null,
    reader: () -> Char
) : IsJsonLikeReader =
    YamlReaderImpl(defaultTag, tagMap, reader)

/** Internal interface for the Yaml Reader functionality */
internal interface IsYamlReader {
    /** Is last character which was read */
    val lastChar: Char
    /** Holds the current char reader */
    var currentReader: YamlCharReader

    /** Reads next Char */
    fun read()
}

internal interface YamlValueType<out T: Any>: ValueType<T> {
    object Binary: YamlValueType<ByteArray>
    object Merge: YamlValueType<Nothing>
    object TimeStamp: YamlValueType<DateTime>
    object Value: YamlValueType<Nothing> //Default value
    object Yaml: YamlValueType<Nothing>
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
    private val reader: () -> Char
) : IsJsonLikeReader, IsYamlReader {
    var version: String? = null

    override var currentToken: JsonToken = JsonToken.StartDocument

    override var lastChar: Char = '\u0000'
    override var currentReader: YamlCharReader = DocumentReader(this)

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
        if (!tagMap.contains(defaultTag)) {
            throw Exception("Default tag should be defined in tag map")
        }
        yamlTagMap.plus(tagMap)
    } ?: yamlTagMap

    override fun nextToken(): JsonToken {
        try {
            this.currentToken = try {
                if (!this.tokenStack.isEmpty()) {
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
                is JsonToken.StartObject, is JsonToken.StartArray -> this.tokenDepth++
                is JsonToken.EndObject, is JsonToken.EndArray -> this.tokenDepth--
                is JsonToken.MergeFieldName -> {
                    this.merges.add(Merge(this.tokenDepth))
                    return this.nextToken()
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
                            return this.nextToken()
                        }
                        this.merges.add(Merge(
                            this.tokenDepth - 1,
                            this.currentToken
                        ))
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
        while(this.lastChar.isWhitespace()) {
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

    override fun skipUntilNextField() {
        val startDepth = this.tokenDepth
        do {
            nextToken()
        } while (
            !(currentToken is JsonToken.FieldName && this.tokenDepth <= startDepth)
            && currentToken !is JsonToken.Stopped
        )
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
        // This line of code fixes a class cast issue in generated JS code.
        @Suppress("USELESS_CAST")
        this.tagMap as Map<String, Map<String, TokenType>>

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
                        ?: throw InvalidYamlContent("Unknown tag $prefix$tag")
            }
            prefix == "!!" -> {
                this.tagMap["tag:yaml.org,2002:"]?.get(tag)
                        ?: throw InvalidYamlContent("Unknown tag $prefix$tag")
            }
            else -> throw InvalidYamlContent("Unknown tag prefix $prefix")
        }
    }

    fun pushToken(token: JsonToken) {
        this.tokenStack.add(token)
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
            is JsonToken.StartArray -> this.isWithArray = true
            is JsonToken.StartObject -> this.isWithArray = false
            else -> throw InvalidYamlContent("Merges should contain Maps or Sequences with maps")
        }
    }
}

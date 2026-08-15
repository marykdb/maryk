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

internal interface YamlCharSource {
    fun read(): Char
}

private class LambdaYamlCharSource(
    private val reader: () -> Char?
) : YamlCharSource {
    override fun read(): Char = reader() ?: throw ExceptionWhileReadingJson()
}

private class StringYamlCharSource(
    private val value: String
) : YamlCharSource {
    private var index = 0

    override fun read(): Char {
        if (index >= value.length) {
            throw ExceptionWhileReadingJson()
        }
        return value[index++]
    }
}

@Suppress("FunctionName")
/** Reads YAML from the supplied [reader]. Return null to signal end of input. */
fun YamlReader(
    defaultTag: String? = null,
    tagMap: Map<String, Map<String, TokenType>>? = null,
    allowUnknownTags: Boolean = false,
    reader: () -> Char?
): IsYamlReader =
    YamlReaderImpl(defaultTag, tagMap, allowUnknownTags, YamlAliasLimits(), reader)

/** Reads YAML from the supplied [reader] with alias replay limits. Return null to signal end of input. */
@Suppress("FunctionName")
fun YamlReader(
    defaultTag: String? = null,
    tagMap: Map<String, Map<String, TokenType>>? = null,
    allowUnknownTags: Boolean = false,
    aliasLimits: YamlAliasLimits,
    reader: () -> Char?
): IsYamlReader =
    YamlReaderImpl(defaultTag, tagMap, allowUnknownTags, aliasLimits, reader)

@Suppress("FunctionName")
/** Reads YAML from the supplied [yaml]. */
fun YamlReader(
    yaml: String,
    defaultTag: String? = null,
    tagMap: Map<String, Map<String, TokenType>>? = null,
    allowUnknownTags: Boolean = false
): IsYamlReader =
    YamlReaderImpl(defaultTag, tagMap, allowUnknownTags, YamlAliasLimits(), StringYamlCharSource(yaml))

/** Reads YAML from the supplied [yaml] with alias replay limits. */
@Suppress("FunctionName")
fun YamlReader(
    yaml: String,
    defaultTag: String? = null,
    tagMap: Map<String, Map<String, TokenType>>? = null,
    allowUnknownTags: Boolean = false,
    aliasLimits: YamlAliasLimits
): IsYamlReader =
    YamlReaderImpl(defaultTag, tagMap, allowUnknownTags, aliasLimits, StringYamlCharSource(yaml))

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
    private val aliasLimits: YamlAliasLimits,
    private val reader: YamlCharSource
) : IsJsonLikeReader, IsInternalYamlReader, IsYamlReader {
    constructor(
        defaultTag: String?,
        tagMap: Map<String, Map<String, TokenType>>?,
        allowUnknownTags: Boolean,
        aliasLimits: YamlAliasLimits,
        reader: () -> Char?
    ) : this(defaultTag, tagMap, allowUnknownTags, aliasLimits, LambdaYamlCharSource(reader))

    var version: String? = null

    override var currentToken: JsonToken = StartDocument

    override var lastChar: Char = '\u0000'
    override var currentReader: IsYamlCharReader = DocumentReader(this)

    private var unclaimedIndenting: Int? = null
    internal var hasException: Boolean = false
    internal val tags: MutableMap<String, String> = mutableMapOf()

    private val anchorReaders = mutableListOf<AnchorRecorder>()
    private val anchorReadersToRemove = mutableListOf<AnchorRecorder>()

    private val tokenStack = YamlTokenQueue()
    private val storedAnchors = mutableMapOf<String, StoredAnchor>()
    private var aliasCount = 0
    private var expandedAliasTokenCount = 0L

    internal val tokenQueueWorkUnits: Long
        get() = this.tokenStack.workUnits

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
                    this.tokenStack.removeFirst()
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
            } catch (_: ExceptionWhileReadingJson) {
                this.hasException = true
                currentReader.handleReaderInterrupt()
            }

            when (currentToken) {
                StartDocument -> {
                    this.storedAnchors.clear()
                    this.aliasCount = 0
                    this.expandedAliasTokenCount = 0
                }
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
                it.recordToken(currentToken, this.tokenDepth) { anchor, tokens, aliasDepth ->
                    val name = anchor.trim()
                    if (name in this.storedAnchors) {
                        throw InvalidYamlContent("Duplicate anchor &$name")
                    }
                    this.storedAnchors[name] = StoredAnchor(tokens, aliasDepth)
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
            if (this.lastChar == '\t') {
                throw InvalidYamlContent("Tabs cannot be used for indentation")
            }
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
        lastChar = reader.read()
    } catch (_: ExceptionWhileReadingJson) {
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
        this.tokenStack.addLast(token)
    }

    fun pushTokenAsFirst(token: JsonToken) {
        this.tokenStack.addFirst(token)
    }

    fun getTokensForAlias(alias: String): Array<JsonToken> {
        val trimmedAlias = alias.trim()
        if (trimmedAlias.isEmpty()) {
            throw InvalidYamlContent("Alias (*) does not contain valid name")
        }

        val storedAnchor = this.storedAnchors[trimmedAlias]
            ?: throw InvalidYamlContent("Unknown alias *$trimmedAlias")
        val aliasDepth = storedAnchor.aliasDepth + 1
        if (aliasDepth > this.aliasLimits.maxAliasDepth) {
            throw InvalidYamlContent(
                "Alias expansion depth budget exceeded: $aliasDepth > ${this.aliasLimits.maxAliasDepth}"
            )
        }

        val nextAliasCount = this.aliasCount + 1
        if (nextAliasCount > this.aliasLimits.maxAliasCount) {
            throw InvalidYamlContent(
                "Alias expansion count budget exceeded: $nextAliasCount > ${this.aliasLimits.maxAliasCount}"
            )
        }

        val nextExpandedTokenCount = this.expandedAliasTokenCount + storedAnchor.tokens.size
        if (nextExpandedTokenCount > this.aliasLimits.maxExpandedTokens) {
            throw InvalidYamlContent(
                "Alias expansion token budget exceeded: $nextExpandedTokenCount > ${this.aliasLimits.maxExpandedTokens}"
            )
        }

        this.aliasCount = nextAliasCount
        this.expandedAliasTokenCount = nextExpandedTokenCount
        this.anchorReaders.forEach { it.recordAliasDepth(aliasDepth) }
        return storedAnchor.tokens
    }

    fun checkAliasNameLength(nameLength: Int, type: String) {
        if (nameLength > this.aliasLimits.maxAliasNameLength) {
            throw InvalidYamlContent(
                "$type name length budget exceeded: $nameLength > ${this.aliasLimits.maxAliasNameLength}"
            )
        }
    }

    fun recordAnchors(anchorReader: AnchorRecorder) {
        anchorReader.setTokenStartDepth(this.tokenDepth)
        this.anchorReaders.add(anchorReader)
    }
}

private class StoredAnchor(
    val tokens: Array<JsonToken>,
    val aliasDepth: Int
)

/** FIFO token queue with a linear-work counter for replay regression coverage. */
internal class YamlTokenQueue {
    private val tokens = ArrayDeque<JsonToken>()

    var workUnits: Long = 0
        private set

    fun isNotEmpty() = this.tokens.isNotEmpty()

    fun addLast(token: JsonToken) {
        this.tokens.addLast(token)
        this.workUnits++
    }

    fun addFirst(token: JsonToken) {
        this.tokens.addFirst(token)
        this.workUnits++
    }

    fun removeFirst(): JsonToken {
        this.workUnits++
        return this.tokens.removeFirst()
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

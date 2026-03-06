package maryk.core.properties.definitions.index

import kotlin.text.CharCategory.COMBINING_SPACING_MARK
import kotlin.text.CharCategory.CONNECTOR_PUNCTUATION
import kotlin.text.CharCategory.CURRENCY_SYMBOL
import kotlin.text.CharCategory.DASH_PUNCTUATION
import kotlin.text.CharCategory.ENCLOSING_MARK
import kotlin.text.CharCategory.END_PUNCTUATION
import kotlin.text.CharCategory.FINAL_QUOTE_PUNCTUATION
import kotlin.text.CharCategory.INITIAL_QUOTE_PUNCTUATION
import kotlin.text.CharCategory.LINE_SEPARATOR
import kotlin.text.CharCategory.MATH_SYMBOL
import kotlin.text.CharCategory.MODIFIER_SYMBOL
import kotlin.text.CharCategory.NON_SPACING_MARK
import kotlin.text.CharCategory.OTHER_PUNCTUATION
import kotlin.text.CharCategory.OTHER_SYMBOL
import kotlin.text.CharCategory.PARAGRAPH_SEPARATOR
import kotlin.text.CharCategory.SPACE_SEPARATOR
import kotlin.text.CharCategory.START_PUNCTUATION

fun normalizeStringForIndex(value: String): String {
    val decomposed = decomposeUnicodeForIndex(value.lowercase())
    val normalized = StringBuilder(decomposed.length)

    for (character in decomposed) {
        when (character.category) {
            NON_SPACING_MARK,
            COMBINING_SPACING_MARK,
            ENCLOSING_MARK,
            SPACE_SEPARATOR,
            LINE_SEPARATOR,
            PARAGRAPH_SEPARATOR,
            CONNECTOR_PUNCTUATION,
            DASH_PUNCTUATION,
            START_PUNCTUATION,
            END_PUNCTUATION,
            INITIAL_QUOTE_PUNCTUATION,
            FINAL_QUOTE_PUNCTUATION,
            OTHER_PUNCTUATION,
            MATH_SYMBOL,
            CURRENCY_SYMBOL,
            MODIFIER_SYMBOL,
            OTHER_SYMBOL -> {}
            else -> normalized.append(normalizedReplacement(character) ?: character)
        }
    }

    return normalized.toString()
}

private fun normalizedReplacement(character: Char): String? = when (character) {
    'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą', 'ǎ', 'ȁ', 'ȃ', 'ạ', 'ả', 'ấ', 'ầ', 'ẩ', 'ẫ', 'ậ', 'ắ', 'ằ', 'ẳ', 'ẵ', 'ặ' -> "a"
    'ç', 'ć', 'ĉ', 'ċ', 'č' -> "c"
    'ď', 'đ' -> "d"
    'è', 'é', 'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę', 'ě', 'ẹ', 'ẻ', 'ẽ', 'ế', 'ề', 'ể', 'ễ', 'ệ' -> "e"
    'ƒ' -> "f"
    'ĝ', 'ğ', 'ġ', 'ģ' -> "g"
    'ĥ', 'ħ' -> "h"
    'ì', 'í', 'î', 'ï', 'ĩ', 'ī', 'ĭ', 'į', 'ı', 'ǐ', 'ị', 'ỉ' -> "i"
    'ĵ' -> "j"
    'ķ', 'ĸ' -> "k"
    'ĺ', 'ļ', 'ľ', 'ŀ', 'ł' -> "l"
    'ñ', 'ń', 'ņ', 'ň', 'ŉ', 'ŋ' -> "n"
    'ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ō', 'ŏ', 'ő', 'ǒ', 'ọ', 'ỏ', 'ố', 'ồ', 'ổ', 'ỗ', 'ộ', 'ơ', 'ớ', 'ờ', 'ở', 'ỡ', 'ợ' -> "o"
    'ŕ', 'ŗ', 'ř' -> "r"
    'ś', 'ŝ', 'ş', 'š', 'ș' -> "s"
    'ß' -> "ss"
    'ţ', 'ť', 'ŧ', 'ț' -> "t"
    'ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ŭ', 'ů', 'ű', 'ų', 'ǔ', 'ụ', 'ủ', 'ứ', 'ừ', 'ử', 'ữ', 'ự' -> "u"
    'ŵ' -> "w"
    'ý', 'ÿ', 'ŷ', 'ỳ', 'ỵ', 'ỷ', 'ỹ' -> "y"
    'ź', 'ż', 'ž' -> "z"
    'æ' -> "ae"
    'œ' -> "oe"
    'ð' -> "d"
    'þ' -> "th"
    else -> null
}

internal expect fun decomposeUnicodeForIndex(value: String): String

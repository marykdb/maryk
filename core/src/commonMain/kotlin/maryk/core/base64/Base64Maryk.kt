package maryk.core.base64

import kotlin.io.encoding.Base64

val Base64Maryk = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

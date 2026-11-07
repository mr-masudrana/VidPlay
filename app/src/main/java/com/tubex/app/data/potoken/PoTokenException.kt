package com.tubex.app.data.potoken

class PoTokenException(message: String) : Exception(message)

/** সিস্টেমের WebView ভাঙা/খুব পুরনো হলে */
class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception =
    if (error.contains("SyntaxError")) BadWebViewException(error) else PoTokenException(error)

package com.textpic.license

/**
 * Tiny JSON reader. The license reply is a flat object with one nested `quota`
 * object and a few strings/numbers, so this stays small and dependency free
 * (which also keeps the logic unit-testable on a plain JVM).
 */
sealed interface JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue

    fun obj(): Obj? = this as? Obj
    fun str(): String? = (this as? Str)?.value
    fun long(): Long? = (this as? Num)?.value?.toLong()
    fun int(): Int? = (this as? Num)?.value?.toInt()
    fun boolean(): Boolean? = (this as? Bool)?.value

    /** Field lookup; null when this is not an object or the field is missing. */
    operator fun get(name: String): JsonValue? = obj()?.fields?.get(name)
}

object Json {
    private const val MAX_DEPTH = 32

    fun parse(text: String): JsonValue? {
        val reader = Reader(text)
        return runCatching {
            val value = reader.readValue(0)
            reader.skipWhitespace()
            if (!reader.atEnd()) null else value
        }.getOrNull()
    }

    private class Reader(private val text: String) {
        private var index = 0

        fun atEnd(): Boolean = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun readValue(depth: Int): JsonValue {
            require(depth <= MAX_DEPTH) { "json too deep" }
            skipWhitespace()
            require(index < text.length) { "unexpected end" }
            return when (val char = text[index]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> JsonValue.Str(readString())
                't' -> readLiteral("true", JsonValue.Bool(true))
                'f' -> readLiteral("false", JsonValue.Bool(false))
                'n' -> readLiteral("null", JsonValue.Null)
                else -> {
                    require(char == '-' || char.isDigit()) { "unexpected character" }
                    readNumber()
                }
            }
        }

        private fun readLiteral(word: String, value: JsonValue): JsonValue {
            require(text.startsWith(word, index)) { "bad literal" }
            index += word.length
            return value
        }

        private fun readObject(depth: Int): JsonValue {
            index++ // {
            val fields = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (index < text.length && text[index] == '}') {
                index++
                return JsonValue.Obj(fields)
            }
            while (true) {
                skipWhitespace()
                require(index < text.length && text[index] == '"') { "expected key" }
                val key = readString()
                skipWhitespace()
                require(index < text.length && text[index] == ':') { "expected colon" }
                index++
                fields[key] = readValue(depth + 1)
                skipWhitespace()
                require(index < text.length) { "unterminated object" }
                when (text[index]) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return JsonValue.Obj(fields)
                    }
                    else -> throw IllegalArgumentException("expected , or }")
                }
            }
        }

        private fun readArray(depth: Int): JsonValue {
            index++ // [
            val items = ArrayList<JsonValue>()
            skipWhitespace()
            if (index < text.length && text[index] == ']') {
                index++
                return JsonValue.Arr(items)
            }
            while (true) {
                items.add(readValue(depth + 1))
                skipWhitespace()
                require(index < text.length) { "unterminated array" }
                when (text[index]) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return JsonValue.Arr(items)
                    }
                    else -> throw IllegalArgumentException("expected , or ]")
                }
            }
        }

        private fun readString(): String {
            index++ // opening quote
            val out = StringBuilder()
            while (true) {
                require(index < text.length) { "unterminated string" }
                when (val char = text[index]) {
                    '"' -> {
                        index++
                        return out.toString()
                    }
                    '\\' -> {
                        index++
                        require(index < text.length) { "bad escape" }
                        when (val escaped = text[index]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(index + 4 < text.length) { "bad unicode escape" }
                                out.append(text.substring(index + 1, index + 5).toInt(16).toChar())
                                index += 4
                            }
                            else -> throw IllegalArgumentException("bad escape")
                        }
                        index++
                    }
                    else -> {
                        out.append(char)
                        index++
                    }
                }
            }
        }

        private fun readNumber(): JsonValue {
            val start = index
            if (index < text.length && text[index] == '-') index++
            while (index < text.length) {
                val char = text[index]
                if (char.isDigit() || char == '.' || char == 'e' || char == 'E' || char == '+' || char == '-') {
                    index++
                } else {
                    break
                }
            }
            val slice = text.substring(start, index)
            return JsonValue.Num(slice.toDouble())
        }
    }
}

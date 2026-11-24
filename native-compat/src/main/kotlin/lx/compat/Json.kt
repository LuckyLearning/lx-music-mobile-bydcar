// SPDX-License-Identifier: Apache-2.0
package lx.compat

/** Small bounded JSON codec: avoids adding an unreviewed Android dependency graph. */
object Json {
    const val MAX_CHARS = 2_097_152
    fun parse(text: String): Any? {
        require(text.length <= MAX_CHARS) { "JSON size limit" }
        return Parser(text).run { val result = value(0); white(); require(i == text.length); result }
    }
    fun stringify(value: Any?): String = StringBuilder().also { write(it, value, 0) }.toString().also {
        require(it.length <= MAX_CHARS) { "JSON size limit" }
    }
    private fun write(b: StringBuilder, v: Any?, depth: Int) {
        require(depth < 48 && b.length <= MAX_CHARS) { "JSON quota" }
        when (v) {
            null -> b.append("null")
            is String -> {
                b.append('"'); for (c in v) when (c) {
                    '"' -> b.append("\\\""); '\\' -> b.append("\\\\"); '\n' -> b.append("\\n")
                    '\r' -> b.append("\\r"); '\t' -> b.append("\\t")
                    else -> if (c.code < 32 || c.code in 0xD800..0xDFFF) b.append("\\u" + c.code.toString(16).padStart(4, '0')) else b.append(c)
                }; b.append('"')
            }
            is Boolean -> b.append(v)
            is Double -> appendNumber(b, v)
            is Float -> appendNumber(b, v.toDouble())
            is Number -> { require(v.toDouble().isFinite()); b.append(v.toString()) }
            is Map<*, *> -> { b.append('{'); v.entries.forEachIndexed { n, e -> if(n>0)b.append(','); require(e.key is String); write(b,e.key,depth+1);b.append(':');write(b,e.value,depth+1) };b.append('}') }
            is Iterable<*> -> { b.append('[');v.forEachIndexed { n,e -> if(n>0)b.append(',');write(b,e,depth+1) };b.append(']') }
            else -> error("Unsupported JSON value")
        }
    }
    private fun appendNumber(b: StringBuilder, d: Double) {
        require(d.isFinite())
        if (d % 1.0 == 0.0 && kotlin.math.abs(d) <= 9_007_199_254_740_992.0) b.append(d.toLong()) else b.append(d.toString())
    }
    private class Parser(val s: String) {
        var i = 0
        fun white() { while(i<s.length && s[i] in " \t\r\n")i++ }
        private fun take(c: Char): Boolean { white(); return if(i<s.length && s[i]==c){i++;true}else false }
        fun value(depth: Int): Any? {
            require(depth<48); white(); require(i<s.length)
            return when(s[i]) {
                '"' -> string()
                '{' -> {i++;val m=linkedMapOf<String,Any?>();if(!take('}')) {do {white();val k=string();require(!m.containsKey(k));require(take(':'));m[k]=value(depth+1)}while(take(','));require(take('}'))};m}
                '[' -> {i++;val a=mutableListOf<Any?>();if(!take(']')) {do {a.add(value(depth+1))}while(take(','));require(take(']'))};a}
                't' -> literal("true",true)
                'f' -> literal("false",false)
                'n' -> literal("null",null)
                else -> {val start=i;while(i<s.length && s[i] in "-+0123456789.eE")i++;val n=s.substring(start,i);require(n.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")));n.toDouble().also{require(it.isFinite())}}
            }
        }
        private fun literal(word:String,v:Any?):Any?{require(s.startsWith(word,i));i+=word.length;return v}
        private fun string():String{
            require(i<s.length&&s[i++]=='"');val b=StringBuilder()
            while(i<s.length){val c=s[i++];if(c=='"')return b.toString();require(c.code>=32);if(c!='\\'){b.append(c);continue};require(i<s.length)
                when(val e=s[i++]){'"','\\','/'->b.append(e);'b'->b.append('\b');'f'->b.append('\u000C');'n'->b.append('\n');'r'->b.append('\r');'t'->b.append('\t');'u'->{require(i+4<=s.length);b.append(s.substring(i,i+4).toInt(16).toChar());i+=4};else->error("JSON escape")}}
            error("Unterminated JSON string")
        }
    }
}
@Suppress("UNCHECKED_CAST") fun Any?.obj(): Map<String, Any?> = this as? Map<String, Any?> ?: error("Expected object")
@Suppress("UNCHECKED_CAST") fun Any?.list(): List<Any?> = this as? List<Any?> ?: error("Expected list")
fun Any?.str(): String = this as? String ?: error("Expected string")
fun Any?.strOrNull(): String? = this as? String
fun Any?.bool(): Boolean = this as? Boolean ?: error("Expected boolean")
fun Any?.num(): Long = (this as? Number)?.toLong() ?: error("Expected number")

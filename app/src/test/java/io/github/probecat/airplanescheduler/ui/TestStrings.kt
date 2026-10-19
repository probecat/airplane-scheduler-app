package io.github.probecat.airplanescheduler.ui

import io.github.probecat.airplanescheduler.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

// The English strings.xml, read the way Android reads it, so tests check the real copy.
object TestStrings : Strings {
    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, Map<String, String>>()

    init {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml")).documentElement
        for (element in root.children("string")) strings[element.getAttribute("name")] = unescape(element.textContent)
        for (element in root.children("plurals")) {
            plurals[element.getAttribute("name")] =
                element.children("item").associate { it.getAttribute("quantity") to unescape(it.textContent) }
        }
    }

    override fun get(id: Int, vararg args: Any): String = format(strings.getValue(name(R.string::class.java, id)), args)

    // English has only "one" and "other".
    override fun plural(id: Int, count: Int, vararg args: Any): String {
        val forms = plurals.getValue(name(R.plurals::class.java, id))
        return format(forms[if (count == 1) "one" else "other"] ?: forms.getValue("other"), args)
    }

    private fun format(text: String, args: Array<out Any>) =
        if (args.isEmpty()) text else String.format(Locale.US, text, *args)

    private fun name(type: Class<*>, id: Int): String = type.fields.first { it.getInt(null) == id }.name

    private fun Element.children(tag: String): List<Element> =
        getElementsByTagName(tag).let { nodes -> List(nodes.length) { nodes.item(it) as Element } }

    private fun unescape(text: String): String {
        val quoted = text.length >= 2 && text.startsWith('"') && text.endsWith('"')
        val raw = if (quoted) text.substring(1, text.length - 1) else text
        return Regex("""\\(u[0-9A-Fa-f]{4}|.)""").replace(raw) { match ->
            val escape = match.groupValues[1]
            when {
                escape.length == 5 -> escape.substring(1).toInt(16).toChar().toString()
                escape == "n" -> "\n"
                escape == "t" -> "\t"
                else -> escape
            }
        }
    }
}

package me.luzility.muuusic.desktop.ui

import org.w3c.dom.Element
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

object Strings {
    private val strings = HashMap<String, String>()
    private val plurals = HashMap<String, Map<String, String>>()

    init {
        val locale = Locale.getDefault()
        val folders = listOfNotNull(
            "values",
            "values-${locale.language}",
            locale.country.takeIf { it.isNotEmpty() }?.let { "values-${locale.language}-r$it" },
        )
        folders.forEach(::load)
    }

    fun get(name: String, vararg args: Any): String {
        val text = strings[name] ?: return name
        return if (args.isEmpty()) text else text.format(*args)
    }

    fun plural(name: String, count: Int, vararg args: Any): String {
        val forms = plurals[name] ?: return name
        val text = (if (count == 1) forms["one"] else null) ?: forms["other"] ?: forms.values.first()
        return text.format(*args.ifEmpty { arrayOf(count) })
    }

    private fun load(folder: String) {
        val stream = Strings::class.java.getResourceAsStream("/strings/$folder/strings.xml") ?: return
        val document = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val nodes = document.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> node.textContent.takeIf { it.isNotBlank() }?.let { strings[name] = unescape(it) }
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    val forms = (0 until items.length).map { items.item(it) as Element }
                        .filter { it.textContent.isNotBlank() }
                        .associate { it.getAttribute("quantity") to unescape(it.textContent) }
                    if (forms.isNotEmpty()) plurals[name] = forms
                }
            }
        }
    }

    private fun unescape(text: String): String {
        val trimmed = text.trim().removeSurrounding("\"")
        val out = StringBuilder()
        var i = 0
        while (i < trimmed.length) {
            val c = trimmed[i]
            if (c == '\\' && i + 1 < trimmed.length) {
                when (val next = trimmed[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'u' -> {
                        out.append(trimmed.substring(i + 2, i + 6).toInt(16).toChar())
                        i += 4
                    }
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}

fun str(name: String, vararg args: Any): String = Strings.get(name, *args)

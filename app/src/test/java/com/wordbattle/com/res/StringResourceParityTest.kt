package com.wordbattle.com.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the bilingual catalogue: every English string must have a Bengali counterpart with the
 * same format placeholders, otherwise a language switch crashes at runtime with
 * `IllegalFormatException` or silently shows an untranslated sentence.
 */
class StringResourceParityTest {

    private val placeholder = Regex("%(\\d+\\\$)?[a-zA-Z]")

    private fun moduleDir(): File {
        // Unit tests run with the module directory as the working directory, but fall back to a
        // search so the test also passes when the runner starts from the repository root.
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "src/main/res/values/strings.xml").exists()) return dir
            if (File(dir, "app/src/main/res/values/strings.xml").exists()) return File(dir, "app")
            dir = dir.parentFile
        }
        error("Could not locate the app module from ${File("").absolutePath}")
    }

    private fun strings(path: String): Map<String, String> {
        val file = File(moduleDir(), path)
        assertTrue("Missing resource file: ${file.absolutePath}", file.exists())
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val name = node.attributes.getNamedItem("name").nodeValue
                put(name, node.textContent.orEmpty())
            }
        }
    }

    private val english by lazy { strings("src/main/res/values/strings.xml") }
    private val bangla by lazy { strings("src/main/res/values-bn/strings.xml") }

    @Test
    fun `both catalogues declare the same keys`() {
        assertEquals(emptySet<String>(), english.keys - bangla.keys)
        assertEquals(emptySet<String>(), bangla.keys - english.keys)
        assertEquals(english.size, bangla.size)
    }

    @Test
    fun `translations keep the same format placeholders`() {
        val mismatched = english.keys.filter { key ->
            placeholder.findAll(english.getValue(key)).map { it.value }.sorted().toList() !=
                placeholder.findAll(bangla.getValue(key)).map { it.value }.sorted().toList()
        }
        assertEquals(emptyList<String>(), mismatched)
    }

    @Test
    fun `no translation is left empty`() {
        assertEquals(emptyList<String>(), bangla.filterValues { it.isBlank() }.keys.toList())
    }
}

package com.yywspace.anethack.identify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Guards the price-identification asset: every object needs a Chinese name and
 * the trade regexes must match the Chinese shop lines NetHack actually prints.
 *
 * org.json is a stub in plain JVM unit tests, so the asset is read as text.
 */
class PriceIDStringsTest {
    private val text: String by lazy {
        listOf("src/main/assets/nh_default_objects.json", "app/src/main/assets/nh_default_objects.json")
            .map(::File)
            .firstOrNull { it.exists() }
            ?.readText()
            ?: error("nh_default_objects.json not found")
    }

    /** Body of the top-level JSON object named [name], brace-matched (values contain braces). */
    private fun regexGroup(name: String): String {
        val start = text.indexOf("\"$name\"")
        check(start >= 0) { "missing $name" }
        val open = text.indexOf('{', start)
        check(open >= 0) { "missing body for $name" }
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(open + 1, i)
                }
            }
        }
        error("unterminated $name")
    }

    @Test
    fun everyObjectHasAChineseName() {
        val names = Regex("\"Name\"\\s*:\\s*\"([^\"]*)\"").findAll(text).map { it.groupValues[1] }.toList()
        val zhNames = Regex("\"NameZh\"\\s*:\\s*\"([^\"]*)\"").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals("every object needs a NameZh", names.size, zhNames.size)
        assertTrue("no objects parsed", names.isNotEmpty())
        zhNames.forEach { assertTrue("empty NameZh", it.isNotBlank()) }
    }

    @Test
    fun chineseNamesAreActuallyChinese() {
        val zhNames = Regex("\"NameZh\"\\s*:\\s*\"([^\"]*)\"").findAll(text).map { it.groupValues[1] }.toList()
        val latin = zhNames.filter { name -> name.any { it in 'a'..'z' || it in 'A'..'Z' } }
        // Only the ASCII-passthrough spellings (scroll chants, T恤衫) may keep Latin letters.
        assertTrue("untranslated names: $latin", latin.all { it == "T恤衫" })
    }

    @Test
    fun buyRegexParsesTheInGamePriceLine() {
        val buy = Pattern.compile(jsonRegex("buy"))
        val matched = buy.matcher("这瓶治愈药水的标价为20 zorkmid.")
        assertTrue(matched.find())
        assertEquals("20", matched.group("objPrice"))

        val plural = buy.matcher("这卷鉴定卷轴的标价为每张30 zorkmid.")
        assertTrue(plural.find())
        assertEquals("30", plural.group("objPrice"))
    }

    @Test
    fun sellRegexParsesTheInGameOfferLine() {
        val sell = Pattern.compile(jsonRegex("sell"))
        val matched = sell.matcher("店主出价仅5 金币购买你的治愈药水. 卖出它?")
        assertTrue(matched.find())
        assertEquals("5", matched.group("objPrice"))
        assertEquals("你的治愈药水", matched.group("objDesc"))
    }

    @Test
    fun objectTypeRegexClassifiesChineseDescriptions() {
        val block = regexGroup("objectTypesRegex")
        val types = Regex("\"([A-Za-z]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .findAll(block).associate { it.groupValues[1] to it.groupValues[2] }

        fun classify(desc: String) = types.entries
            .firstOrNull { Pattern.compile(it.value).matcher(desc).find() }?.key

        assertEquals("Potion", classify("这瓶治愈药水"))
        assertEquals("Ring", classify("你的装饰品戒指"))
        assertEquals("Boots", classify("你的速度靴"))
        assertEquals("Gloves", classify("你的敏捷手套"))
        assertEquals("Wand", classify("你的挖掘魔杖"))
    }

    private fun jsonRegex(key: String): String {
        val block = regexGroup("tradeTypesRegex")
        val m = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(block)
            ?: error("missing regex $key")
        return m.groupValues[1].replace("\\\\", "\\")
    }
}

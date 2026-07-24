package com.thelightphone.lp3keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeLexicon(vararg entries: Pair<String, Long>) : SvLexicon {
    private val freq = entries.toMap()
    private val offensiveWords = HashSet<String>()
    override val ready = true
    override val sorted: Array<String> = freq.keys.toTypedArray().also { it.sort() }
    override fun isWord(word: String) = word in freq
    override fun isSafeTarget(word: String) = word in freq && word !in offensiveWords
    override fun freqOf(word: String) = freq[word] ?: 0L
    fun flagOffensive(word: String) = apply { offensiveWords.add(word) }
}

class SvCorrectorTest {

    private val dict = FakeLexicon(
        "hej" to 200, "hejdå" to 150, "köttbullar" to 100, "vinter" to 140,
        "sommar" to 150, "stuga" to 120, "fotboll" to 140, "plan" to 160,
        "så" to 210, "och" to 215, "året" to 150, "mötet" to 140,
    )

    @Test
    fun `adjacent-key slip on the o key fixes to ö`() {
        // o and ö are grid neighbours (row 1 col 9 / row 2 col 10)
        assertEquals("köttbullar", SvCorrector.correct("kottbullar", dict))
    }

    @Test
    fun `adjacent-key slip on the å key fixes from p`() {
        // p and å are grid neighbours (row 1 cols 10/11)
        assertEquals("hejdå", SvCorrector.correct("hejdp", dict))
    }

    @Test
    fun `doubled key is removed`() {
        assertEquals("hej", SvCorrector.correct("hejj", dict))
    }

    @Test
    fun `transposition is fixed`() {
        assertEquals("vinter", SvCorrector.correct("vitner", dict))
    }

    @Test
    fun `known words are left alone`() {
        assertNull(SvCorrector.correct("hej", dict))
        assertNull(SvCorrector.correct("köttbullar", dict))
    }

    @Test
    fun `unknown compound of two known words is left alone`() {
        assertNull(SvCorrector.correct("sommarstuga", dict))
    }

    @Test
    fun `unknown compound with linking-s is left alone`() {
        assertNull(SvCorrector.correct("fotbollsplan", dict))
    }

    @Test
    fun `offensive words are never suggested`() {
        val d = FakeLexicon("hej" to 200, "hejdå" to 150).flagOffensive("hejdå")
        assertNull(SvCorrector.correct("hejdp", d))
    }

    @Test
    fun `case of the typed word is preserved by applyCase`() {
        assertEquals("Köttbullar", SvCorrector.applyCase("Kottbullar", "köttbullar"))
        assertEquals("HEJDÅ", SvCorrector.applyCase("HEJDP", "hejdå"))
        assertEquals("hejdå", SvCorrector.applyCase("hejdp", "hejdå"))
    }

    @Test
    fun `trailing word extraction handles Swedish letters`() {
        assertEquals("hejdå", SvCorrector.trailingWord("vi ses, hejdå"))
        assertEquals("", SvCorrector.trailingWord("vi ses! "))
    }

    @Test
    fun `non-word gibberish far from anything is left alone`() {
        assertNull(SvCorrector.correct("zzqzzq", dict))
    }
}

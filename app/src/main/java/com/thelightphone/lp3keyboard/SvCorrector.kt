package com.thelightphone.lp3keyboard

/**
 * Keyboard-aware autocorrect for the Swedish layout. Engine ported (trimmed) from
 * KEZO555/Type (MIT), which itself follows the classic noisy-channel design:
 * generate every 1-edit variant of the typed word, keep the real words, and rank
 * them by how plausible the slip is (transposition and adjacent-key substitution
 * beat arbitrary edits), then by corpus frequency.
 *
 * Pure JVM — no Android imports — so it is unit-testable on the host.
 */
object SvCorrector {

    const val ALPHABET = "abcdefghijklmnopqrstuvwxyzåäö"
    private val ROWS = listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm")

    // Edit costs, lowest = most likely a real typo.
    private const val COST_TRANSPOSE = 0
    private const val COST_ADJACENT = 1
    private const val COST_INDEL = 2
    private const val COST_SUB = 3
    private const val COST_DOUBLE = COST_ADJACENT   // doubled-key slip ("hejj")

    /** A fix above this edit cost is a wild guess — never auto-applied. */
    private const val CONFIDENT_MAX_COST = COST_INDEL

    /** 2-letter words only accept a transposition or adjacent-key slip. */
    private const val SHORT_WORD_MAX_COST = COST_ADJACENT

    // A candidate one edit costlier than the cheapest still wins if it is far
    // more frequent. The dictionary's f is AOSP log-scale (0-255, ~20 units per
    // order of magnitude), so "25x more frequent" is a delta, not a ratio.
    private const val FREQ_PROMOTE_DELTA = 28L

    private const val MIN_LEN_DIST2 = 6
    private const val MIN_COMPOUND_PART = 3

    private val adjacency: Map<Char, String> = buildAdjacency(ROWS)

    /** A character that's part of a word for autocorrect: any letter, plus apostrophe. */
    fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\''

    /** A character that finishes a word and may trigger autocorrect. */
    fun isCorrectTrigger(c: Char): Boolean = c.isWhitespace() || c in ".,!?;:)"

    /** The run of word characters at the end of [before] (immediately before the cursor). */
    fun trailingWord(before: CharSequence): String {
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        return before.subSequence(i, before.length).toString()
    }

    /** Match the fix's case to what the user typed. */
    fun applyCase(original: String, fix: String): String = when {
        original.length > 1 && original.all { it.isUpperCase() } -> fix.uppercase()
        original.firstOrNull()?.isUpperCase() == true -> fix.replaceFirstChar { it.uppercaseChar() }
        else -> fix
    }

    /**
     * The confident correction for [word] (typed as-is, any case), or null. Applies
     * the Swedish compound guard: a word that splits into two known words at any
     * point (e.g. "sommarstuga") is treated as correct, since Swedish compounds
     * freely and no word list contains them all.
     */
    fun correct(word: String, dict: SvLexicon): String? {
        if (!dict.ready || word.length < 2 || word.length > 24) return null
        val lower = word.lowercase()
        if (lower.any { it !in ALPHABET && it != '\'' }) return null
        if (dict.isWord(lower)) return null
        if (isCompoundOfKnown(lower, dict)) return null
        val costOut = IntArray(1)
        val fix = bestCorrection(lower, dict, costOut) ?: return null
        val maxCost = if (lower.length == 2) SHORT_WORD_MAX_COST else CONFIDENT_MAX_COST
        return if (costOut[0] <= maxCost) fix else null
    }

    private fun isCompoundOfKnown(word: String, dict: SvLexicon): Boolean {
        for (i in MIN_COMPOUND_PART..word.length - MIN_COMPOUND_PART) {
            if (!dict.isWord(word.substring(i))) continue
            val head = word.substring(0, i)
            if (dict.isWord(head)) return true
            // Swedish linking-s ("fogen"): drift+s+äkerhet -> driftsäkerhet.
            if (head.length > MIN_COMPOUND_PART && head.last() == 's' &&
                dict.isWord(head.dropLast(1))
            ) return true
        }
        return false
    }

    private fun bestCorrection(word: String, dict: SvLexicon, costOut: IntArray): String? {
        var best: String? = null
        var bestCost = Int.MAX_VALUE
        var bestFreq = -1L
        val freqByCost = arrayOfNulls<String>(COST_SUB + 2)
        val freqAtCost = LongArray(COST_SUB + 2) { -1L }

        fun consider(cand: String, cost: Int) {
            if (cand == word || !dict.isSafeTarget(cand)) return
            val f = dict.freqOf(cand)
            if (cost < bestCost || (cost == bestCost && f > bestFreq)) {
                bestCost = cost; bestFreq = f; best = cand
            }
            val ci = cost.coerceIn(0, freqAtCost.size - 1)
            if (f > freqAtCost[ci]) { freqAtCost[ci] = f; freqByCost[ci] = cand }
        }

        for (i in 0..word.length) {
            val l = word.substring(0, i)
            val r = word.substring(i)
            if (r.isNotEmpty()) {
                val dblDel = (l.isNotEmpty() && l.last() == r[0]) || (r.length > 1 && r[1] == r[0])
                consider(l + r.substring(1), if (dblDel) COST_DOUBLE else COST_INDEL)
                if (r.length > 1) consider(l + r[1] + r[0] + r.substring(2), COST_TRANSPOSE)
                val typed = r[0]
                val near = adjacency[typed].orEmpty()
                for (c in ALPHABET) {
                    if (c == typed) continue
                    consider(l + c + r.substring(1), if (c in near) COST_ADJACENT else COST_SUB)
                }
            }
            for (c in ALPHABET) {
                val dblIns = (l.isNotEmpty() && l.last() == c) || (r.isNotEmpty() && r[0] == c)
                consider(l + c + r, if (dblIns) COST_DOUBLE else COST_INDEL)
            }
        }
        if (best != null) {
            // Noisy-channel promotion: a far more frequent candidate one step
            // costlier than the cheapest fix wins.
            val cap = (bestCost + 1).coerceAtMost(freqAtCost.size - 1)
            var promo: String? = null; var promoFreq = -1L; var promoCost = 0
            for (c in 0..cap) if (freqAtCost[c] > promoFreq) {
                promoFreq = freqAtCost[c]; promo = freqByCost[c]; promoCost = c
            }
            if (promo != null && promo != best && bestFreq > 0L &&
                promoFreq >= bestFreq + FREQ_PROMOTE_DELTA
            ) {
                costOut[0] = promoCost
                return promo
            }
            costOut[0] = bestCost
            return best
        }
        val d2 = bestCorrectionDist2(word, dict)
        if (d2 != null) costOut[0] = COST_INDEL
        return d2
    }

    /**
     * Conservative distance-2 fallback for longer words: only dictionary words
     * sharing the typed word's first and last letter, within ±2 in length.
     */
    private fun bestCorrectionDist2(word: String, dict: SvLexicon): String? {
        if (word.length < MIN_LEN_DIST2) return null
        val sorted = dict.sorted
        val first = word[0]
        val last = word[word.length - 1]
        val firstStr = first.toString()
        var lo = 0; var hi = sorted.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (sorted[mid] < firstStr) lo = mid + 1 else hi = mid }
        var best: String? = null
        var bestFreq = -1L
        var i = lo
        while (i < sorted.size && sorted[i].isNotEmpty() && sorted[i][0] == first) {
            val cand = sorted[i]; i++
            if (cand.length < word.length - 2 || cand.length > word.length + 2) continue
            if (cand[cand.length - 1] != last || cand == word) continue
            if (levAtMost(word, cand, 2) <= 2) {
                val f = dict.freqOf(cand)
                if (f > bestFreq) { bestFreq = f; best = cand }
            }
        }
        return best
    }

    private fun levAtMost(a: String, b: String, max: Int): Int {
        val la = a.length; val lb = b.length
        if (kotlin.math.abs(la - lb) > max) return max + 1
        var prev = IntArray(lb + 1) { it }
        for (i in 1..la) {
            val cur = IntArray(lb + 1)
            cur[0] = i
            var rowMin = cur[0]
            val ai = a[i - 1]
            for (j in 1..lb) {
                val cost = if (ai == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return max + 1
            prev = cur
        }
        return prev[lb]
    }

    /**
     * Neighbours from the key grid: same or adjacent row, within one column
     * (ignoring stagger — plenty for typo correction).
     */
    private fun buildAdjacency(rows: List<String>): Map<Char, String> {
        val pos = HashMap<Char, Pair<Int, Int>>()
        rows.forEachIndexed { r, row -> row.forEachIndexed { c, ch -> pos[ch] = r to c } }
        val out = HashMap<Char, String>()
        for ((ch, rc) in pos) {
            val (r, c) = rc
            val sb = StringBuilder()
            for ((other, orc) in pos) {
                if (other == ch) continue
                if (kotlin.math.abs(orc.first - r) <= 1 && kotlin.math.abs(orc.second - c) <= 1) sb.append(other)
            }
            out[ch] = sb.toString()
        }
        return out
    }
}

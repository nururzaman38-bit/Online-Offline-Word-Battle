package com.wordbattle.com.data.dictionary

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Loads ENABLE once and performs O(1), case-insensitive lookups. */
class WordDictionary private constructor(private val words: Set<String>) {
    val size: Int get() = words.size

    fun isValidWord(word: String): Boolean =
        word.length >= MIN_WORD_LENGTH && words.contains(word.uppercase(Locale.ROOT))

    companion object {
        const val MIN_WORD_LENGTH = 2
        private const val ASSET_PATH = "dictionary/enable1.txt"

        // Keeps debug builds playable until the user-provided ENABLE asset is installed.
        private val developmentFallback = setOf(
            "AA", "AB", "AD", "AE", "AG", "AH", "AI", "AL", "AM", "AN", "AR", "AS", "AT", "AW", "AX", "AY",
            "BA", "BE", "BI", "BO", "BY", "DA", "DE", "DO", "ED", "EF", "EH", "EL", "EM", "EN", "ER", "ES", "ET",
            "EX", "FA", "FE", "GI", "GO", "GU", "HA", "HE", "HI", "HM", "HO", "ID", "IF", "IN", "IO", "IS", "IT",
            "JO", "KA", "KI", "KO", "KY", "LA", "LI", "LO", "MA", "ME", "MI", "MM", "MO", "MU", "MY", "NA", "NE",
            "NG", "NI", "NO", "NU", "OD", "OE", "OF", "OH", "OI", "OK", "OM", "ON", "OP", "OR", "OS", "OW", "OX",
            "OY", "PA", "PE", "PI", "PO", "QI", "RE", "SH", "SI", "SO", "TA", "TE", "TI", "TO", "UH", "UM", "UN",
            "UP", "US", "UT", "WE", "WO", "XI", "XU", "YA", "YE", "YO", "ZA",
            "WORD", "BATTLE", "GAME", "PLAY", "PLAYER", "COMPUTER", "FRIEND", "HELLO", "WORLD", "KOTLIN", "ANDROID",
            "CAT", "DOG", "HOUSE", "TREE", "READ", "READY", "RACK", "BOARD", "SCORE", "WIN", "WINNER"
        )

        suspend fun load(context: Context): WordDictionary = withContext(Dispatchers.IO) {
            val loaded = runCatching {
                context.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                    lines.map(String::trim)
                        .filter { it.length >= MIN_WORD_LENGTH && !it.startsWith("#") }
                        .map { it.uppercase(Locale.ROOT) }
                        .toHashSet()
                }
            }.getOrElse { emptySet() }
            WordDictionary(if (loaded.isEmpty()) developmentFallback else loaded)
        }

        fun fromWords(words: Iterable<String>): WordDictionary = WordDictionary(
            words.map(String::trim)
                .filter { it.length >= MIN_WORD_LENGTH }
                .map { it.uppercase(Locale.ROOT) }
                .toHashSet()
        )
    }
}

package com.gridpointcode.core

import java.text.Normalizer
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reading a code to a listener in their own language's words. */
class SpellingsTest {

    /** The specification's example, `#G3RJM-98NM9*T`. */
    private val example = PlaceState(selectionAt(Point(43.650006, -79.380004), Source.SAMPLE))

    private fun plain(word: String) = Normalizer.normalize(word, Normalizer.Form.NFD).first().uppercaseChar()

    @Test
    fun everyWordBeginsWithItsLetterBecauseTheListenerKeepsOnlyThat() {
        for (spelling in SPELLINGS) {
            assertEquals("CDFGHJKLMNPRTWX".toSet(), spelling.letters.keys, spelling.name)
            for ((letter, word) in spelling.letters) {
                assertEquals(letter, plain(word), "${spelling.name}: $word for $letter")
            }
            assertEquals(15, spelling.letters.values.toSet().size, "${spelling.name}: a word used twice")
        }
    }

    @Test
    fun everyLanguageSaysTenDifferentNumbers() {
        for (spelling in SPELLINGS) {
            assertEquals(10, spelling.digits.size, spelling.name)
            assertEquals(10, spelling.digits.toSet().size, spelling.name)
        }
    }

    @Test
    fun eachLanguageIsOfferedOnceUnderItsOwnName() {
        assertEquals(SPELLINGS.size, SPELLINGS.map { it.tag }.toSet().size)
        assertEquals(SPELLINGS.map { it.name }.sortedBy { plain(it) }, SPELLINGS.map { it.name }, "in the order of their own names")
        for (spelling in SPELLINGS) {
            assertTrue(spelling.emergency.contains("%1\$s") && spelling.emergency.contains("%2\$s"), spelling.name)
        }
    }

    @Test
    fun theListenersLanguageIsFoundByLanguageNotCountry() {
        assertEquals("de", spellingFor(Locale.GERMANY).tag)
        assertEquals("de", spellingFor(Locale.forLanguageTag("de-AT")).tag)
        assertEquals("fr", spellingFor(Locale.CANADA_FRENCH).tag)
        assertEquals("en", spellingFor(Locale.forLanguageTag("en-IN")).tag)
        assertEquals("en", spellingFor(Locale.forLanguageTag("hi-IN")).tag, "a language with no set here gets the international one")
        assertEquals("sv", spellingTagged("sv")?.tag)
        assertNull(spellingTagged("xx"))
    }

    @Test
    fun theCodeIsTheSameInEveryLanguageAndOnlyTheWordsChange() {
        val code = example.selection.code
        assertEquals(
            "Goslar, drei, Rostock, Jena, München; neun, acht, Nürnberg, München, neun; Prüfzeichen Tübingen",
            aloud(code, spellingFor(Locale.GERMAN)),
        )
        assertEquals(
            "Gaston, trois, Raoul, Joseph, Marcel; neuf, huit, Nicolas, Marcel, neuf; contrôle Thérèse",
            aloud(code, spellingFor(Locale.FRENCH)),
        )
        assertEquals(
            "Genova, tre, Roma, Jolly, Milano; nove, otto, Napoli, Milano, nove; controllo Torino",
            aloud(code, spellingFor(Locale.ITALIAN)),
        )
        assertEquals("Golf, three, Romeo, Juliett, Mike", aloudArea("G3RJM"))
        assertEquals("Gustav, tre, Rudolf, Johan, Martin", aloudArea("G3RJM", spellingFor(Locale.forLanguageTag("sv"))))
    }

    @Test
    fun twoIsSaidSoItCannotBeHeardAsThree() {
        assertEquals("zwo, drei", aloudArea("23", spellingFor(Locale.GERMAN)))
        assertEquals("één, twee", aloudArea("12", spellingFor(Locale.forLanguageTag("nl"))))
    }

    @Test
    fun coordinatesAreSaidAsWrittenDigitByDigitAfterThePoint() {
        assertEquals(
            "43 point six five zero zero zero six, minus 79 point three eight zero zero zero four",
            aloudCoordinates("43.650006, -79.380004"),
        )
        assertEquals(
            "43 Komma sechs fünf null null null sechs, minus 79 Komma drei acht null null null vier",
            aloudCoordinates("43.650006, -79.380004", spellingFor(Locale.GERMAN)),
        )
        assertEquals("0 coma cero, 12", aloudCoordinates("0.0, 12", spellingFor(Locale.forLanguageTag("es"))))
    }

    @Test
    fun theViewSpeaksEveryLineInTheListenersWords() {
        val italian = spellingFor(Locale.ITALIAN)
        val view = example.view(Locale.CANADA, italian)
        assertEquals(italian, view.listener)
        assertTrue(view.spoken.startsWith("Genova, tre"))
        assertEquals("Genova, tre, Roma, Jolly, Milano", view.areas.first { it.level == 5 }.spoken)
        assertEquals("Genova, tre, Roma, Jolly, Milano", example.widened(5).view(listener = italian).area?.spoken)
        assertEquals(aloud(example.selection.code), example.view().spoken, "the international words when nobody chose")
    }

    @Test
    fun theEmergencyCardIsReadWhollyInTheListenersLanguage() {
        val here = Point(43.650006, -79.380004)
        val fixed = example.locating().located(here, 2.0)
        val card = assertIs<Emergency.Here>(emergencyOf(fixed.view(Locale.CANADA, spellingFor(Locale.ITALIAN))))
        assertEquals(
            "Latitudine e longitudine: 43 virgola sei cinque zero zero zero sei, meno 79 virgola tre otto zero zero zero quattro. " +
                "Grid Point Code: Genova, tre, Roma, Jolly, Milano; nove, otto, Napoli, Milano, nove; controllo Torino.",
            card.said,
        )
        assertEquals("43.650006, -79.380004", card.decimal, "the written coordinates keep their full stop")
        val english = assertIs<Emergency.Here>(emergencyOf(fixed.view()))
        assertTrue(english.said.startsWith("Latitude and longitude: 43 point six five"))
    }
}

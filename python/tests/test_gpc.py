#  Copyright 2017 Pranavkumar Patel
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

"""Version 2, case by case.

The worked examples come from SPEC.md rather than from running the code, so a
change in behaviour shows up as a failure here instead of quietly becoming the
new expected value.
"""

import unittest

from src.gridpointcode_algo_pranavpatel_ca import GEOMETRIC, GPC, GPCError, INVALID, RESERVED


class TestEncode(unittest.TestCase):
    """Section 5, and the worked examples of 5.5."""

    def test_the_worked_examples(self):
        for latitude, longitude, expected in [
                (43.65000, -79.38000, "#G3RJM-98NM9"),
                (43.64260, -79.38710, "#G3RJM-0M6DX"),
                (23.02250, 72.57140, "#KDC8X-JM49X"),
                (-33.85680, 151.21530, "#6LK4X-NRP0R"),
                (-13.16310, -72.54500, "#C8HKC-13C80"),
                (64.14660, -21.94260, "#RDX9R-TN19T")]:
            with self.subTest(latitude=latitude, longitude=longitude):
                self.assertEqual(expected, GPC.encode(latitude, longitude))

    def test_the_poles_encode(self):
        self.assertEqual("#P4444-PPPPP", GPC.encode(90.0, 0.0))
        self.assertEqual("#3PPPP-00000", GPC.encode(-90.0, 0.0))

    def test_the_antimeridian_is_one_place_with_one_code(self):
        self.assertEqual("#F0000-00000", GPC.encode(0.0, -180.0))
        self.assertEqual("#F0000-00000", GPC.encode(0.0, 180.0))
        # 179.99999999999999 is exactly 180.0 once stored as a double.
        self.assertEqual("#F0000-00000", GPC.encode(0.0, 179.99999999999999))

    def test_negative_zero_is_the_same_point(self):
        self.assertEqual("#JPPPP-00000", GPC.encode(0.0, 0.0))
        self.assertEqual("#JPPPP-00000", GPC.encode(-0.0, -0.0))
        self.assertEqual("#JPPPP-00000", GPC.encode(0.0, -0.0))
        self.assertEqual("#JPPPP-00000", GPC.encode(-0.0, 0.0))

    def test_the_formatted_form_is_the_unformatted_one_with_separators(self):
        self.assertEqual("G3RJM98NM9", GPC.encode(43.65, -79.38, False))
        self.assertEqual("#G3RJM-98NM9", GPC.encode(43.65, -79.38, True))
        self.assertEqual("#G3RJM-98NM9", GPC.format_gpc("G3RJM98NM9"))

    def test_every_code_is_ten_characters(self):
        for latitude, longitude in [(0, 0), (90, 180), (-90, -180),
                                    (43.65, -79.38), (-13.1631, -72.545)]:
            self.assertEqual(10, len(GPC.encode(latitude, longitude, False)))

    def test_no_encoded_code_begins_with_x(self):
        """Level 1 produces the indices 0 to 23 only, so X is unreachable."""
        for latitude in range(-90, 91, 5):
            for longitude in range(-180, 181, 5):
                code = GPC.encode(float(latitude), float(longitude), False)
                self.assertNotEqual("X", code[0])

    def test_coordinates_outside_the_domain_are_rejected(self):
        for latitude, longitude, reason in [
                (90.00001, 0.0, "LATITUDE"), (-90.00001, 0.0, "LATITUDE"),
                (1000.0, 0.0, "LATITUDE"), (0.0, 180.00001, "LONGITUDE"),
                (0.0, -180.00001, "LONGITUDE"), (0.0, 1000.0, "LONGITUDE"),
                (float("nan"), 0.0, "LATITUDE"), (float("inf"), 0.0, "LATITUDE"),
                (0.0, float("nan"), "LONGITUDE"), (0.0, float("-inf"), "LONGITUDE")]:
            with self.subTest(latitude=latitude, longitude=longitude):
                with self.assertRaises(GPCError) as caught:
                    GPC.encode(latitude, longitude)
                self.assertEqual(reason, caught.exception.reason)
                self.assertEqual(reason + ": value out of valid range.",
                                 str(caught.exception))

    def test_the_domain_includes_its_own_edges(self):
        for latitude, longitude in [(90.0, 0.0), (-90.0, 0.0),
                                    (0.0, 180.0), (0.0, -180.0),
                                    (90.0, 180.0), (-90.0, -180.0)]:
            self.assertEqual((True, ""), GPC.is_valid_coordinates(latitude, longitude))


class TestDecode(unittest.TestCase):
    """Section 6, and the worked examples of 6.4."""

    def test_the_worked_examples(self):
        for code, expected in [("#G3RJM-98NM9", (43.650006, -79.380004)),
                               ("#KDC8X-JM49X", (23.022501, 72.571407)),
                               ("#6LK4X-NRP0R", (-33.856808, 151.215314)),
                               ("#P4444-PPPPP", (89.999988, 0.000015)),
                               ("#JPPPP-00000", (0.000012, 0.000015))]:
            with self.subTest(code=code):
                self.assertEqual(expected, GPC.decode(code))

    def test_a_code_decodes_to_the_centre_of_its_cell(self):
        south, west, north, east = GPC.decode_to_area("#G3RJM-98NM9")
        latitude, longitude = GPC.decode("#G3RJM-98NM9")
        self.assertLess(south, latitude)
        self.assertLess(latitude, north)
        self.assertLess(west, longitude)
        self.assertLess(longitude, east)

    def test_the_area_of_the_corner_cells_reaches_the_edge_of_the_world(self):
        """A box is a closed region, so it may name +90 and +180."""
        self.assertEqual(90.0, GPC.decode_to_area("P4444PPPPP")[2])
        self.assertEqual(-90.0, GPC.decode_to_area("3PPPP00000")[0])
        self.assertEqual(-180.0, GPC.decode_to_area("F000000000")[1])

    def test_separators_and_case_do_not_matter(self):
        expected = GPC.decode("G3RJM98NM9")
        for form in ["#G3RJM-98NM9", "g3rjm98nm9", "  G3RJM 98NM9  ",
                     "#g3rjm-98nm9", "--G3RJM98NM9##"]:
            with self.subTest(form=form):
                self.assertEqual(expected, GPC.decode(form))

    def test_round_trip_is_stable(self):
        for latitude, longitude in [(43.65, -79.38), (90.0, 0.0), (-90.0, 0.0),
                                    (0.0, -180.0), (0.0, 0.0), (-33.8568, 151.2153)]:
            code = GPC.encode(latitude, longitude, False)
            self.assertEqual(code, GPC.encode(*GPC.decode(code), formatted=False))

    def test_typed_errors(self):
        for code, reason in [("XG3RJ98NM9", "GPC_RESERVED"),
                             ("", "GPC_NULL"), ("   ", "GPC_NULL"), (None, "GPC_NULL"),
                             ("G3RJM98NM", "GPC_LENGTH"), ("G3RJM98NM999", "GPC_LENGTH"),
                             ("G3RJM98NMQ", "GPC_CHAR"), ("G3RJM98NMU", "GPC_CHAR"),
                             ("G3RJM98NMY", "GPC_CHAR"), ("#G3RJM-98NM9*5", "GPC_CHECK")]:
            with self.subTest(code=repr(code)):
                with self.assertRaises(GPCError) as caught:
                    GPC.decode(code)
                self.assertEqual(reason, caught.exception.reason)

    def test_eleven_characters_are_read_as_version_1(self):
        """The dispatch is on length alone, so an eleven-character string that
        happens to be a valid version 1 code decodes as one -- even when what
        the caller meant was a version 2 code with a character too many. This
        is the price of carrying both formats in one install, and it is why
        section 15.2 says to show the decoded point on a map before acting on
        it."""
        self.assertEqual(("INVALID", "GPC_LENGTH"), GPC.validate("G3RJM98NM99"))
        self.assertEqual((True, ""), GPC.is_valid_v1("G3RJM98NM99"))
        self.assertEqual(GPC.decode_v1("G3RJM98NM99"), GPC.decode("G3RJM98NM99"))

    def test_a_reserved_code_has_no_area_either(self):
        with self.assertRaises(GPCError) as caught:
            GPC.decode_to_area("XG3RJ98NM9")
        self.assertEqual("GPC_RESERVED", caught.exception.reason)

    def test_the_error_is_a_value_error(self):
        """Version 1 raised ValueError. Existing handlers keep working."""
        with self.assertRaises(ValueError):
            GPC.decode("nonsense")


class TestParsing(unittest.TestCase):
    """Section 8."""

    def test_the_alias_table(self):
        for typed, meant in [("O", "0"), ("I", "1"), ("S", "5"), ("Z", "2"),
                             ("B", "8"), ("A", "4"), ("E", "3"), ("V", "W")]:
            with self.subTest(typed=typed):
                self.assertEqual(GPC.decode("G3RJM98NM" + meant),
                                 GPC.decode("G3RJM98NM" + typed))

    def test_l_is_a_symbol_and_is_never_read_as_one(self):
        self.assertTrue(GPC.is_valid("G3RJM98NML"))
        self.assertNotEqual(GPC.decode("G3RJM98NML"), GPC.decode("G3RJM98NM1"))

    def test_u_q_and_y_are_rejected_rather_than_aliased(self):
        for character in "UQY":
            with self.subTest(character=character):
                self.assertEqual((INVALID, "GPC_CHAR"),
                                 GPC.validate("G3RJM98NM" + character))

    def test_case_folding_is_ascii_only(self):
        self.assertEqual(GPC.decode("G3RJM98NM9"), GPC.decode("g3rjm98nm9"))

    def test_the_whitespace_set_is_ascii_and_is_the_same_everywhere(self):
        """Space, tab, line feed, vertical tab, form feed and carriage return,
        and nothing wider. A port that also stripped the Unicode spaces would
        accept what another port rejects, which is the whole thing the shared
        vectors exist to prevent."""
        expected = GPC.decode("G3RJM98NM9")
        for space in [" ", "\t", "\n", "\v", "\f", "\r"]:
            with self.subTest(space=repr(space)):
                self.assertEqual(
                    expected, GPC.decode(space + "G3RJM" + space + "98NM9" + space))
                self.assertEqual((INVALID, "GPC_NULL"), GPC.validate(space * 3))
        # U+00A0 is a space to Unicode and a symbol outside this alphabet.
        self.assertEqual((INVALID, "GPC_CHAR"), GPC.validate("\u00a03RJM98NM9"))

    def test_normalisation_is_idempotent(self):
        once, _ = GPC.normalise("#g3rjm-98nm9")
        twice, _ = GPC.normalise(once)
        self.assertEqual("G3RJM98NM9", once)
        self.assertEqual(once, twice)


class TestClassify(unittest.TestCase):
    """Section 9 and Appendix C."""

    def test_the_three_classes(self):
        self.assertEqual(GEOMETRIC, GPC.classify("#G3RJM-98NM9"))
        self.assertEqual(RESERVED, GPC.classify("XG3RJ98NM9"))
        self.assertEqual(INVALID, GPC.classify("nope"))

    def test_reserved_is_not_valid_and_is_not_a_typing_error(self):
        self.assertFalse(GPC.is_valid("XXXXXXXXXX"))
        self.assertEqual((RESERVED, ""), GPC.validate("XXXXXXXXXX"))

    def test_reasons_are_tested_in_order(self):
        for text, expected in [("", (INVALID, "GPC_NULL")),
                               ("Q", (INVALID, "GPC_LENGTH")),
                               ("QQQQQQQQQQ", (INVALID, "GPC_CHAR"))]:
            with self.subTest(text=repr(text)):
                self.assertEqual(expected, GPC.validate(text))

    def test_a_version_1_code_is_not_a_version_2_code(self):
        """classify describes this grid, and eleven characters are not in it."""
        self.assertEqual((INVALID, "GPC_LENGTH"), GPC.validate("#FN5G-CDKL-HDC"))
        self.assertFalse(GPC.is_valid("#FN5G-CDKL-HDC"))
        self.assertEqual((True, ""), GPC.is_valid_v1("#FN5G-CDKL-HDC"))


class TestCheckCharacter(unittest.TestCase):
    """Section 14."""

    def test_the_worked_examples(self):
        for code, check in [("#G3RJM-98NM9", "T"), ("#KDC8X-JM49X", "D"),
                            ("#P4444-PPPPP", "2"), ("#JPPPP-00000", "M")]:
            with self.subTest(code=code):
                self.assertEqual(check, GPC.check_character(code))

    def test_a_correct_check_character_is_accepted_and_stripped(self):
        self.assertEqual(GPC.decode("#G3RJM-98NM9"), GPC.decode("#G3RJM-98NM9*T"))
        self.assertTrue(GPC.is_valid("#G3RJM-98NM9*T"))
        self.assertEqual(GEOMETRIC, GPC.classify("#g3rjm-98nm9*t"))

    def test_a_wrong_check_character_fails_everywhere(self):
        """Never a silent ignore, and never valid-but-undecodable."""
        for text in ["#G3RJM-98NM9*5", "#G3RJM-98NM9*", "#G3RJM-98NM9*TT",
                     "#G3RJM-98NM9*Q"]:
            with self.subTest(text=text):
                self.assertEqual((INVALID, "GPC_CHECK"), GPC.validate(text))
                self.assertFalse(GPC.is_valid(text))
                with self.assertRaises(GPCError):
                    GPC.decode(text)

    def test_every_single_symbol_error_is_detected(self):
        alphabet = "0123456789CDFGHJKLMNPRTWX"
        code = "G3RJM98NM9"
        check = GPC.check_character(code)
        for position in range(10):
            for symbol in alphabet:
                if symbol == code[position]:
                    continue
                wrong = code[:position] + symbol + code[position + 1:]
                self.assertEqual((INVALID, "GPC_CHECK"),
                                 GPC.validate(wrong + "*" + check))

    def test_every_adjacent_transposition_is_detected(self):
        code = "G3RJM98NM9"
        check = GPC.check_character(code)
        for position in range(9):
            if code[position] == code[position + 1]:
                continue
            swapped = (code[:position] + code[position + 1] + code[position]
                       + code[position + 2:])
            self.assertEqual((INVALID, "GPC_CHECK"),
                             GPC.validate(swapped + "*" + check))

    def test_a_reserved_code_has_a_check_character_like_any_other(self):
        self.assertEqual(RESERVED, GPC.classify("XG3RJ98NM9*"
                                                + GPC.check_character("XG3RJ98NM9")))


if __name__ == "__main__":
    unittest.main()

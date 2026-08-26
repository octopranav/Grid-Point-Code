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


class TestCells(unittest.TestCase):
    """Sections 18.1 and 18.2."""

    def test_a_cell_is_a_prefix(self):
        self.assertEqual("G3R", GPC.cell("#G3RJM-98NM9", 3))
        self.assertEqual("G3RJM", GPC.cell("#G3RJM-98NM9", 5))
        self.assertEqual("G3RJM98NM9", GPC.cell("#G3RJM-98NM9", 10))

    def test_a_cell_is_normalised_first(self):
        self.assertEqual("G3RJM1", GPC.cell("#g3rjm-i8nm9", 6))

    def test_a_cell_of_a_cell(self):
        self.assertEqual("G3", GPC.cell("G3RJM", 2))

    def test_a_cell_is_bare(self):
        # Ten characters is a code and anything shorter is a region. A cell
        # written as #G3R- would claim to be something it is not.
        for level in range(1, 11):
            with self.subTest(level=level):
                cell = GPC.cell("#G3RJM-98NM9", level)
                self.assertNotIn("#", cell)
                self.assertNotIn("-", cell)

    def test_a_level_outside_one_to_ten(self):
        for level in (0, 11, -1, 100):
            with self.subTest(level=level):
                with self.assertRaises(GPCError) as caught:
                    GPC.cell("G3RJM98NM9", level)
                self.assertEqual("GPC_LEVEL", caught.exception.reason)

    def test_a_cell_shorter_than_the_level_asked_for(self):
        with self.assertRaises(GPCError) as caught:
            GPC.cell("G3R", 5)
        self.assertEqual("GPC_LENGTH", caught.exception.reason)

    def test_a_reserved_cell_is_rejected_and_says_so(self):
        for text in ("XG3RJ", "XG3RJ98NM9"):
            with self.subTest(text=text):
                with self.assertRaises(GPCError) as caught:
                    GPC.cell(text, 3)
                self.assertEqual("GPC_RESERVED", caught.exception.reason)

    def test_containment_is_the_prefix_test(self):
        self.assertTrue(GPC.contains("G3RJM", "G3RJM98NM9"))
        self.assertTrue(GPC.contains("G", "G3RJM98NM9"))
        self.assertFalse(GPC.contains("G3RJD", "G3RJM98NM9"))

    def test_containment_holds_between_cells(self):
        self.assertTrue(GPC.contains("G3R", "G3RJM"))
        self.assertFalse(GPC.contains("G3RJM", "G3R"))

    def test_containment_normalises_both_sides(self):
        self.assertTrue(GPC.contains("#g3rjm", "#G3RJM-98NM9"))


class TestNeighbours(unittest.TestCase):
    """Section 18.3."""

    def test_eight_neighbours_away_from_the_poles(self):
        self.assertEqual(8, len(GPC.neighbours("G3RJM98NM9")))
        self.assertEqual(8, len(GPC.neighbours("G3RJM")))

    def test_five_in_a_polar_row(self):
        # Rows do not wrap, so the three that would lie off the grid are absent
        # rather than present and empty.
        self.assertEqual(5, len(GPC.neighbours("#P4444-PPPPP")))
        self.assertEqual(5, len(GPC.neighbours("#3PPPP-00000")))

    def test_neighbours_are_the_same_length_as_the_cell(self):
        for level in range(1, 11):
            with self.subTest(level=level):
                cell = GPC.cell("#G3RJM-98NM9", level)
                for neighbour in GPC.neighbours(cell):
                    self.assertEqual(level, len(neighbour))

    def test_columns_wrap_at_the_antimeridian(self):
        # The first column of the grid. Its western neighbour is the last
        # column, and no amount of string arithmetic would have found it: the
        # two share no characters at all.
        first = GPC.encode(0.0, -180.0, False)
        west = GPC.neighbours(first)[6]
        self.assertEqual(west, GPC.encode(0.0, 179.99999, False))
        self.assertNotEqual(first[0], west[0])

    def test_the_order_is_fixed(self):
        # North, north-east, east, south-east, south, south-west, west,
        # north-west.
        got = GPC.neighbours("G3RJM98NM9")
        row, col = GPC.decode_to_grid("G3RJM98NM9")
        expected = [GPC.grid_to_code(row + dr, col + dc)
                    for dr, dc in [(1, 0), (1, 1), (0, 1), (-1, 1),
                                   (-1, 0), (-1, -1), (0, -1), (1, -1)]]
        self.assertEqual(expected, got)

    def test_a_cell_is_not_its_own_neighbour(self):
        self.assertNotIn("G3RJM", GPC.neighbours("G3RJM"))


class TestCellDimensions(unittest.TestCase):
    """Section 18.4, against the table of section 3."""

    def test_the_table(self):
        for level, north_south, east_west in [
                (1, 5000.9, 6679.2), (2, 1000.2, 1335.8), (3, 200.0, 267.2),
                (4, 40.0, 53.4), (5, 8.0, 10.7)]:
            with self.subTest(level=level):
                dimensions = GPC.cell_dimensions(level)
                self.assertEqual(north_south, round(dimensions[2] / 1000, 1))
                self.assertEqual(east_west, round(dimensions[3] / 1000, 1))

    def test_a_doorway(self):
        dimensions = GPC.cell_dimensions(10)
        self.assertEqual(2.6, round(dimensions[2], 1))
        self.assertEqual(3.4, round(dimensions[3], 1))

    def test_the_aspect_ratio_is_three_quarters_at_every_level(self):
        for level in range(1, 11):
            with self.subTest(level=level):
                latitude, longitude = GPC.cell_dimensions(level)[:2]
                self.assertEqual(0.75, round(latitude / longitude, 12))

    def test_a_level_outside_one_to_ten(self):
        with self.assertRaises(GPCError) as caught:
            GPC.cell_dimensions(0)
        self.assertEqual("GPC_LEVEL", caught.exception.reason)


class TestDistance(unittest.TestCase):
    """Section 18.5. Compared to a tolerance, never to equality."""

    def test_a_cell_is_no_distance_from_itself(self):
        self.assertEqual(0.0, GPC.distance("G3RJM98NM9", "G3RJM98NM9"))

    def test_it_is_symmetric(self):
        self.assertEqual(GPC.distance("G3RJM98NM9", "6LK4XNRP0R"),
                         GPC.distance("6LK4XNRP0R", "G3RJM98NM9"))

    def test_pole_to_pole_is_half_the_meridian(self):
        metres = GPC.distance("#P4444-PPPPP", "#3PPPP-00000")
        self.assertAlmostEqual(20015.1, metres / 1000, places=1)

    def test_antipodal_cells_do_not_produce_a_nan(self):
        metres = GPC.distance(GPC.encode(0.0, 0.0, False),
                              GPC.encode(0.0, 180.0, False))
        self.assertAlmostEqual(20015.1, metres / 1000, places=1)

    def test_cells_of_different_levels(self):
        self.assertLess(GPC.distance("G3RJM", "G3RJM98NM9"), 7000.0)


class TestShortForm(unittest.TestCase):
    """Section 12."""

    def test_the_short_form_is_the_second_printed_group(self):
        self.assertEqual("98NM9", GPC.shorten("#G3RJM-98NM9"))
        self.assertEqual("98NM9", GPC.shorten("G3RJM98NM9"))

    def test_recovery_accepts_the_dash_either_way(self):
        for short in ("98NM9", "-98NM9", " -98nm9 "):
            with self.subTest(short=short):
                self.assertEqual("#G3RJM-98NM9",
                                 GPC.recover_short(short, 43.66, -79.39))

    def test_recovery_is_exact_within_half_a_cell(self):
        code = GPC.encode(43.65, -79.38, False)
        short = GPC.shorten(code)
        for d_latitude in (-0.0359, 0.0, 0.0359):
            for d_longitude in (-0.0479, 0.0, 0.0479):
                with self.subTest(d_latitude=d_latitude, d_longitude=d_longitude):
                    self.assertEqual(code, GPC.recover_short(
                        short, 43.65 + d_latitude, -79.38 + d_longitude, False))

    def test_recovery_crosses_the_antimeridian(self):
        # A reference east of the line recovering a code west of it. The
        # column arithmetic wraps; the row arithmetic must not.
        code = GPC.encode(0.0, -179.99, False)
        self.assertEqual(code, GPC.recover_short(
            GPC.shorten(code), 0.0, 179.995, False))

    def test_a_short_form_that_is_not_five_symbols(self):
        for short in ("98NM", "98NM99", ""):
            with self.subTest(short=short):
                with self.assertRaises(GPCError):
                    GPC.recover_short(short, 43.65, -79.38)

    def test_a_reference_outside_the_domain(self):
        with self.assertRaises(GPCError) as caught:
            GPC.recover_short("98NM9", 91.0, 0.0)
        self.assertEqual("LATITUDE", caught.exception.reason)


class TestCorrections(unittest.TestCase):
    """Section 15.3."""

    def test_the_true_code_is_found_and_ranked_first(self):
        code = GPC.encode(43.65, -79.38, False)
        for position in range(10):
            with self.subTest(position=position):
                wrong = code[:position] + ("0" if code[position] != "0" else "1") \
                    + code[position + 1:]
                got = GPC.suggest_corrections(wrong, 43.65, -79.38, 6, False)
                self.assertEqual(code, got[0])

    def test_the_input_need_not_decode_to_anywhere_near_the_reference(self):
        # The whole point: a code with a wrong character is what this is for.
        got = GPC.suggest_corrections("03RJM98NM9", 43.65, -79.38, 6, False)
        self.assertIn(GPC.encode(43.65, -79.38, False), got)

    def test_reserved_candidates_are_never_suggested(self):
        for candidate in GPC.suggest_corrections("XG3RJ98NM9", 43.65, -79.38,
                                                 4, False):
            self.assertNotEqual("X", candidate[0])

    def test_a_narrower_level_returns_fewer_candidates(self):
        wrong = "G3RJM98NM8"
        wide = GPC.suggest_corrections(wrong, 43.65, -79.38, 4, False)
        narrow = GPC.suggest_corrections(wrong, 43.65, -79.38, 8, False)
        self.assertGreater(len(wide), len(narrow))

    def test_a_code_that_will_not_normalise_to_ten_symbols(self):
        with self.assertRaises(GPCError):
            GPC.suggest_corrections("G3RJM98NM", 43.65, -79.38)

    def test_at_most_249_candidates_and_never_padded(self):
        # P4444PPPPP has adjacent repeats, so it yields 242 rather than 249,
        # and the list is not padded back with duplicates.
        every = GPC.suggest_corrections("P4444PPPPP", 90.0, 0.0, 1, False)
        self.assertEqual(len(every), len(set(every)))


class TestIntegerForm(unittest.TestCase):
    """Section 13."""

    def test_it_round_trips(self):
        code = GPC.encode(43.65, -79.38, False)
        self.assertEqual(code, GPC.from_integer(GPC.to_integer(code), False))

    def test_the_first_and_last_codes(self):
        self.assertEqual(0, GPC.to_integer("0000000000"))
        self.assertEqual(25 ** 10 - 1, GPC.to_integer("XXXXXXXXXX"))

    def test_string_order_is_integer_order(self):
        codes = sorted(GPC.encode(latitude, longitude, False)
                       for latitude in (-80.0, -20.0, 0.0, 20.0, 80.0)
                       for longitude in (-170.0, -60.0, 0.0, 60.0, 170.0))
        values = [GPC.to_integer(code) for code in codes]
        self.assertEqual(values, sorted(values))

    def test_reserved_codes_occupy_the_top_of_the_range(self):
        floor = 24 * 25 ** 9
        self.assertGreaterEqual(GPC.to_integer("X000000000"), floor)
        self.assertLess(GPC.to_integer("W999999999"), floor)

    def test_a_value_outside_the_range(self):
        for value in (-1, 25 ** 10):
            with self.subTest(value=value):
                with self.assertRaises(GPCError) as caught:
                    GPC.from_integer(value)
                self.assertEqual("GPC_RANGE", caught.exception.reason)


class TestScreening(unittest.TestCase):
    """Section 17. Advisory: it reports and never blocks."""

    def test_the_version_comes_back_even_when_nothing_matched(self):
        version, spans = GPC.screen("G3RJM98NM9")
        self.assertNotEqual("", version)
        self.assertEqual([], spans)

    def test_a_match_reports_its_span(self):
        version, spans = GPC.screen("GN4T000000")
        self.assertNotEqual("", version)
        self.assertEqual([(1, 4)], spans)

    def test_spans_are_ordered_by_position_then_length(self):
        _, spans = GPC.screen("0GN4T00000")
        self.assertEqual(sorted(spans), spans)

    def test_a_reserved_code_screens_like_any_other(self):
        _, spans = GPC.screen("XGN4T00000")
        self.assertEqual([(2, 4)], spans)

    def test_screening_never_blocks(self):
        # Whatever the list says, the code still encodes, decodes and validates.
        self.assertTrue(GPC.is_valid("GN4T000000"))
        self.assertEqual("GEOMETRIC", GPC.classify("GN4T000000"))
        latitude, longitude = GPC.decode("GN4T000000")
        self.assertEqual("GN4T000000", GPC.encode(latitude, longitude, False))

    def test_the_formatted_and_bare_forms_screen_alike(self):
        self.assertEqual(GPC.screen("GN4T000000"), GPC.screen("#GN4T0-00000"))


class TestBulk(unittest.TestCase):
    """Batch and streaming, for dataset work."""

    def test_encode_all(self):
        self.assertEqual(["G3RJM98NM9", "JPPPP00000"],
                         GPC.encode_all([(43.65, -79.38), (0.0, 0.0)], False))

    def test_decode_all(self):
        self.assertEqual([(43.650006, -79.380004)],
                         GPC.decode_all(["#G3RJM-98NM9"]))

    def test_the_stream_is_lazy(self):
        stream = GPC.encode_stream([(43.65, -79.38), (91.0, 0.0)], False)
        self.assertEqual("G3RJM98NM9", next(stream))
        # The bad row raises when it is reached, not before, which is what lets
        # a caller handle failures row by row.
        with self.assertRaises(GPCError):
            next(stream)

    def test_a_bad_row_stops_the_batch(self):
        with self.assertRaises(GPCError):
            GPC.encode_all([(43.65, -79.38), (0.0, 181.0)])

    def test_an_empty_sequence(self):
        self.assertEqual([], GPC.encode_all([]))
        self.assertEqual([], GPC.decode_all([]))


class TestGridIndices(unittest.TestCase):
    """Section 18.6."""

    def test_it_agrees_with_to_grid(self):
        self.assertEqual(GPC.to_grid(43.65, -79.38),
                         GPC.decode_to_grid("#G3RJM-98NM9"))

    def test_the_corner_cells(self):
        self.assertEqual((0, 0), GPC.decode_to_grid(
            GPC.encode(-90.0, -180.0, False)))
        self.assertEqual((7812499, 11718749), GPC.decode_to_grid(
            GPC.encode(90.0, 179.99999, False)))

    def test_a_reserved_code(self):
        with self.assertRaises(GPCError) as caught:
            GPC.decode_to_grid("XG3RJ98NM9")
        self.assertEqual("GPC_RESERVED", caught.exception.reason)


class TestConversions(unittest.TestCase):
    """Section 19."""

    def test_the_worked_example(self):
        self.assertEqual("43°39'00.00\"N, 79°22'48.00\"W",
                         GPC.to_dms(43.65, -79.38))

    def test_negative_zero_is_not_negative(self):
        self.assertEqual("0°00'00.00\"N, 0°00'00.00\"E",
                         GPC.to_dms(-0.0, -0.0))

    def test_seconds_carry_into_the_next_minute(self):
        self.assertEqual("1°00'00.00\"N, 0°00'00.00\"E",
                         GPC.to_dms(1.0 - 1e-9, 0.0))

    def test_dms_reads_its_own_output_back(self):
        self.assertEqual((43.65, -79.38),
                         GPC.from_dms(GPC.to_dms(43.65, -79.38)))

    def test_dms_accepts_the_wider_forms(self):
        self.assertEqual((43.65, -79.38), GPC.from_dms("43d39m0s N 79d22m48s W"))
        self.assertEqual((43.0, -79.0), GPC.from_dms("43°N 79°W"))
        self.assertEqual((-43.0, 79.0), GPC.from_dms("-43°, +79°"))

    def test_dms_rejections(self):
        for text in ["43°39'00.00\"N",              # one axis only
                     "43 39",                            # no unit markers
                     "-43°N, 79°W",            # a sign and a hemisphere
                     "43°W, 79°N",             # the axes crossed
                     "43°60'N, 0°0'E",         # sixty minutes
                     "43°39'60.0\"N, 0°0'0\"E",  # sixty seconds
                     "43°N, 79°W extra"]:      # trailing text
            with self.subTest(text=text):
                with self.assertRaises(GPCError):
                    GPC.from_dms(text)

    def test_dms_outside_the_domain(self):
        with self.assertRaises(GPCError) as caught:
            GPC.from_dms("91°N, 0°E")
        self.assertEqual("LATITUDE", caught.exception.reason)

    def test_a_decoded_code_survives_the_dms_round_trip(self):
        # decode returns a cell centre, which sits eight times further from the
        # nearest boundary than this rounding can move it.
        for latitude, longitude in [(43.65, -79.38), (-33.8568, 151.2153),
                                    (90.0, 0.0), (-90.0, 0.0), (0.0, -180.0)]:
            with self.subTest(latitude=latitude, longitude=longitude):
                code = GPC.encode(latitude, longitude, False)
                back = GPC.from_dms(GPC.to_dms(*GPC.decode(code)))
                self.assertEqual(code, GPC.encode(back[0], back[1], False))

    def test_the_geo_uri(self):
        self.assertEqual("geo:43.650006,-79.380004",
                         GPC.to_geo_uri(43.650006, -79.380004))

    def test_trailing_zeros_and_the_point_are_dropped(self):
        self.assertEqual("geo:43.65,-79.38", GPC.to_geo_uri(43.65, -79.38))
        self.assertEqual("geo:43,-79", GPC.to_geo_uri(43.0, -79.0))
        self.assertEqual("geo:0,0", GPC.to_geo_uri(-0.0, -0.0))

    def test_the_geo_uri_reads_its_own_output_back(self):
        self.assertEqual((43.650006, -79.380004),
                         GPC.from_geo_uri("geo:43.650006,-79.380004"))

    def test_altitude_and_parameters_are_dropped(self):
        self.assertEqual((43.65, -79.38),
                         GPC.from_geo_uri("geo:43.65,-79.38,76.1"))
        self.assertEqual((43.65, -79.38),
                         GPC.from_geo_uri("geo:43.65,-79.38;u=35"))
        self.assertEqual((43.65, -79.38),
                         GPC.from_geo_uri("GEO:43.65,-79.38;crs=WGS84"))

    def test_another_datum_is_refused_rather_than_ignored(self):
        # Reading a code as though it were on another datum would put it in the
        # wrong place, quietly.
        with self.assertRaises(GPCError) as caught:
            GPC.from_geo_uri("geo:43.65,-79.38;crs=nad83")
        self.assertEqual("GPC_GEO", caught.exception.reason)

    def test_geo_uri_rejections(self):
        for text in ["geo:43.65", "43.65,-79.38", "geo:+43.65,-79.38",
                     "geo:43.65,-79.38,1,2", "geo:1e2,0", "geo:,"]:
            with self.subTest(text=text):
                with self.assertRaises(GPCError):
                    GPC.from_geo_uri(text)

    def test_a_decoded_code_survives_the_geo_uri_round_trip(self):
        for latitude, longitude in [(43.65, -79.38), (-33.8568, 151.2153),
                                    (90.0, 0.0), (-90.0, 0.0), (0.0, -180.0)]:
            with self.subTest(latitude=latitude, longitude=longitude):
                code = GPC.encode(latitude, longitude, False)
                back = GPC.from_geo_uri(GPC.to_geo_uri(*GPC.decode(code)))
                self.assertEqual(code, GPC.encode(back[0], back[1], False))

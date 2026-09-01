"""Tests for the fuzzer's own judgement.

A fuzzer nobody has watched fail is a fuzzer nobody knows works. Ninety
thousand cases and no faults reads exactly the same as ninety thousand cases
and no working checks, so every check here is given answers that break it and
answers that do not.

These need no drivers and no toolchains: `faults` decides from parsed output,
so it can be handed the output directly.

    python -m unittest discover --start-directory conformance --top-level-directory conformance
"""

from __future__ import annotations

import unittest

from fuzz import LAT_CELL, LNG_CELL, apart, faults, generate, transmissible
import random


def four(value):
    """The same answer from every port."""
    return {"python": value, "typescript": value, "java": value, "csharp": value}


class Crashes(unittest.TestCase):
    def test_one_port_crashing_is_a_fault(self):
        answers = {"fz.encode.0": dict(four("#G3RJM-98NM9"),
                                       csharp="EXC:NullReferenceException")}
        self.assertEqual([f[0] for f in faults(answers, {})], ["crash"])

    def test_all_four_crashing_is_still_a_fault(self):
        """The trap this whole file exists to guard.

        Four ports that crash identically agree perfectly, and a harness that
        only diffs them would call that a clean run.
        """
        answers = {"fz.encode.0": four("EXC:NullReferenceException")}
        self.assertEqual([f[0] for f in faults(answers, {})], ["crash"])

    def test_a_documented_error_is_not_a_crash(self):
        answers = {"fz.encode.0": four("ERR:LATITUDE")}
        self.assertEqual(faults(answers, {}), [])


class Divergence(unittest.TestCase):
    def test_one_port_differing_is_a_fault(self):
        answers = {"fz.encode.0": dict(four("#G3RJM-98NM9"), java="#G3RJM-98NM8")}
        self.assertEqual([f[0] for f in faults(answers, {})], ["divergence"])

    def test_agreement_passes(self):
        self.assertEqual(faults({"fz.encode.0": four("#G3RJM-98NM9")}, {}), [])

    def test_the_fixed_battery_is_left_alone(self):
        """compare.py owns those labels; judging them here would double-report."""
        answers = {"withCheck": dict(four("A"), java="B")}
        self.assertEqual(faults(answers, {}), [])


class Contradiction(unittest.TestCase):
    """Valid, and then undecodable. This shipped in 1.0 and was fixed in 1.1.0."""

    def test_valid_then_refused_is_a_fault(self):
        answers = {
            "fz.isvalid.0": four("true"),
            "fz.decode.0": four("ERR:GPC_RANGE"),
        }
        found = faults(answers, {"fz.isvalid.0": ("CCCCCCCCCC",)})
        self.assertEqual([f[0] for f in found], ["contradiction"] * 4)

    def test_valid_and_decodable_passes(self):
        answers = {
            "fz.isvalid.0": four("true"),
            "fz.decode.0": four("43.65,-79.38"),
        }
        self.assertEqual(faults(answers, {"fz.isvalid.0": ("G3RJM98NM9",)}), [])

    def test_invalid_and_refused_passes(self):
        """Saying no twice is consistent, which is all this check asks for."""
        answers = {
            "fz.isvalid.0": four("false"),
            "fz.decode.0": four("ERR:GPC_RANGE"),
        }
        self.assertEqual(faults(answers, {"fz.isvalid.0": ("CCCCCCCCCC",)}), [])

    def test_one_port_contradicting_itself_is_named(self):
        answers = {
            "fz.isvalid.0": dict(four("false"), java="true"),
            "fz.decode.0": four("ERR:GPC_RANGE"),
        }
        found = faults(answers, {"fz.isvalid.0": ("CCCCCCCCCC",)})
        kinds = [f[0] for f in found]
        self.assertIn("contradiction", kinds)
        self.assertIn("java", [f[2].split()[0] for f in found if f[0] == "contradiction"])


class Longitude(unittest.TestCase):
    def test_the_antimeridian_is_not_a_gap(self):
        self.assertEqual(apart(180.0, -180.0), 0.0)

    def test_across_the_antimeridian_is_the_short_way(self):
        self.assertAlmostEqual(apart(179.0, -179.0), 2.0)

    def test_ordinary_distance_is_unchanged(self):
        self.assertAlmostEqual(apart(10.0, 4.0), 6.0)

    def test_it_does_not_care_which_way_round(self):
        self.assertEqual(apart(-179.0, 179.0), apart(179.0, -179.0))


class Cells(unittest.TestCase):
    """The allowance is a cell, and a cell has a size the grid decides."""

    def test_a_cell_is_the_grid_divided_down(self):
        self.assertAlmostEqual(LAT_CELL, 180.0 / 7812500)
        self.assertAlmostEqual(LNG_CELL, 360.0 / 11718750)

    def test_longitude_cells_are_wider_than_latitude_cells(self):
        self.assertGreater(LNG_CELL, LAT_CELL)


class CaseFile(unittest.TestCase):
    def test_a_bar_cannot_be_sent(self):
        self.assertFalse(transmissible("G3RJM|8NM9"))

    def test_a_line_break_cannot_be_sent(self):
        self.assertFalse(transmissible("G3RJM\n98NM9"))

    def test_an_ordinary_code_can(self):
        self.assertTrue(transmissible("#G3RJM-98NM9"))

    def test_generated_cases_are_all_one_line_each(self):
        cases, _ = generate(random.Random(11), 200)
        for case in cases:
            self.assertNotIn("\n", case)
            self.assertGreaterEqual(len(case.split("|")), 3)

    def test_a_seed_reproduces_its_cases_exactly(self):
        """Reproducibility is the whole reason the seed is printed."""
        first, _ = generate(random.Random(4), 100)
        second, _ = generate(random.Random(4), 100)
        self.assertEqual(first, second)

    def test_different_seeds_produce_different_cases(self):
        first, _ = generate(random.Random(4), 100)
        second, _ = generate(random.Random(5), 100)
        self.assertNotEqual(first, second)

    def test_the_generator_reaches_the_edges_of_the_world(self):
        """Uniform sampling would essentially never land on a pole."""
        cases, _ = generate(random.Random(3), 3000)
        encodes = [c for c in cases if c.split("|")[1] == "encode"]
        edges = [c for c in encodes
                 if any(part in ("90.0", "-90.0", "180.0", "-180.0", "0.0", "-0.0")
                        for part in c.split("|")[2:])]
        self.assertGreater(len(edges), 0)


if __name__ == "__main__":
    unittest.main()

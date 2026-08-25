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

"""Version 1 codes still resolve.

There is no version 1 encoder here, so every case below starts from a code.
The codes are ones the 1.1.0 release produced, and the coordinates are what it
returned for them: this file exists to catch the day the legacy path stops
agreeing with what is already printed on things.
"""

import unittest

from src.gridpointcode_algo_pranavpatel_ca import GPC, GPCError


class TestVersion1(unittest.TestCase):
    """Appendix B."""

    def test_decode_dispatches_on_length(self):
        """Eleven characters is version 1, ten is version 2. Nothing else."""
        self.assertEqual((43.65, -79.38), GPC.decode("#FN5G-CDKL-HDC"))
        self.assertEqual((43.650006, -79.380004), GPC.decode("#G3RJM-98NM9"))

    def test_the_explicit_entry_point_agrees_with_the_dispatch(self):
        for code in ["#FN5G-CDKL-HDC", "FN5GCDKLHDC", "#HG9K-PCVH-DPV"]:
            with self.subTest(code=code):
                self.assertEqual(GPC.decode(code), GPC.decode_v1(code))

    def test_version_1_returns_the_corner_of_its_cell(self):
        """Version 2 returns the centre. This difference is deliberate: the
        value is the one every version 1 release has returned."""
        self.assertEqual((0.0, 0.0), GPC.decode_v1("DCCCCCCCCCC"))
        self.assertEqual((89.99999, 179.99999), GPC.decode_v1("HG9KPCVHDPV"))
        self.assertEqual((-89.99999, -179.99999), GPC.decode_v1("HG9PJLHJX69"))

    def test_separators_and_case_do_not_matter(self):
        expected = (43.65, -79.38)
        for form in ["#FN5G-CDKL-HDC", "FN5GCDKLHDC", "fn5gcdklhdc",
                     "  FN5GCDKLHDC  ", "FN5G CDKL HDC"]:
            with self.subTest(form=form):
                self.assertEqual(expected, GPC.decode_v1(form))

    def test_validity(self):
        for code, expected in [("#FN5G-CDKL-HDC", (True, "")),
                               ("DCCCCCCCCCC", (True, "")),
                               ("", (False, "GPC_NULL")),
                               ("   ", (False, "GPC_NULL")),
                               (None, (False, "GPC_NULL")),
                               ("ABC", (False, "GPC_LENGTH")),
                               ("FN5GCDKLHDCC", (False, "GPC_LENGTH")),
                               ("FN5GCDKLHDA", (False, "GPC_CHAR")),
                               ("CCCCCCCCCCC", (False, "GPC_RANGE")),
                               ("YYYYYYYYYYY", (False, "GPC_RANGE"))]:
            with self.subTest(code=repr(code)):
                self.assertEqual(expected, GPC.is_valid_v1(code))

    def test_typed_errors(self):
        for code, reason in [("", "GPC_NULL"), ("ABC", "GPC_LENGTH"),
                             ("FN5GCDKLHDA", "GPC_CHAR"),
                             ("CCCCCCCCCCC", "GPC_RANGE")]:
            with self.subTest(code=repr(code)):
                with self.assertRaises(GPCError) as caught:
                    GPC.decode_v1(code)
                self.assertEqual(reason, caught.exception.reason)

    def test_the_version_2_alias_table_never_touches_a_version_1_code(self):
        """V and Y are version 1 symbols. Version 2 excludes both, reads V as W
        and rejects Y outright, and none of that may reach this path."""
        self.assertEqual((True, ""), GPC.is_valid_v1("#HG9K-PCVH-DPV"))
        self.assertEqual((89.99999, 179.99999), GPC.decode("#HG9K-PCVH-DPV"))
        self.assertEqual((False, "GPC_RANGE"), GPC.is_valid_v1("9999999999Y"))

    def test_there_is_no_version_1_encoder(self):
        self.assertFalse(hasattr(GPC, "encode_v1"))
        self.assertEqual(10, len(GPC.encode(43.65, -79.38, False)))


if __name__ == "__main__":
    unittest.main()

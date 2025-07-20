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

import unittest
from src.gpc_algo_pranavpatel_ca import GPC

# Unit tests for the GPC (Grid Point Code) encoding and decoding functions
class TestGPC(unittest.TestCase):

    # Test encoding and decoding of origin (0,0)
    def test_min_zero(self):
        self.assertEqual("#DCCC-CCCC-CCC", GPC.encode(0, 0))
        self.assertEqual((0, 0), GPC.decode("#DCCC-CCCC-CCC"))
    
    # Test minimum positive coordinates (smallest possible value)
    def test_min1(self):
        self.assertEqual("#DCCC-CCCC-CCR", GPC.encode(0.00001, 0.00001))
        self.assertEqual((0.00001, 0.00001), GPC.decode("#DCCC-CCCC-CCR"))
    
    # Test encoding/decoding for negative latitude and positive longitude
    def test_min2(self):
        self.assertEqual("#DCCD-7Y5W-LLH", GPC.encode(-0.00001, 0.00001))
        self.assertEqual((-0.00001, 0.00001), GPC.decode("#DCCD-7Y5W-LLH"))

    # Test encoding/decoding for positive latitude and negative longitude
    def test_min3(self):
        self.assertEqual("#DCCC-8473-0G4", GPC.encode(0.00001, -0.00001))
        self.assertEqual((0.00001, -0.00001), GPC.decode("#DCCC-8473-0G4"))

    # Test encoding/decoding for both negative latitude and longitude
    def test_min4(self):
        self.assertEqual("#DCCG-5K1D-WV7", GPC.encode(-0.00001, -0.00001))
        self.assertEqual((-0.00001, -0.00001), GPC.decode("#DCCG-5K1D-WV7"))

    # Test maximum valid positive coordinates
    def test_max1(self):
        self.assertEqual("#HG9K-PCVH-DPV", GPC.encode(89.99999, 179.99999))
        self.assertEqual((89.99999, 179.99999), GPC.decode("#HG9K-PCVH-DPV"))

    # Test maximum valid negative latitude and positive longitude
    def test_max2(self):
        self.assertEqual("#HG9N-KTKR-83Y", GPC.encode(-89.99999, 179.99999))
        self.assertEqual((-89.99999, 179.99999), GPC.decode("#HG9N-KTKR-83Y"))

    # Test maximum valid positive latitude and negative longitude
    def test_max3(self):
        self.assertEqual("#HG9M-L0M1-M0K", GPC.encode(89.99999, -179.99999))
        self.assertEqual((89.99999, -179.99999), GPC.decode("#HG9M-L0M1-M0K"))

    # Test maximum valid negative coordinates
    def test_max4(self):
        self.assertEqual("#HG9P-JLHJ-X69", GPC.encode(-89.99999, -179.99999))
        self.assertEqual((-89.99999, -179.99999), GPC.decode("#HG9P-JLHJ-X69"))

    # Test rounding behavior when encoding with more than 5 decimal places
    def test_truncate(self):
        self.assertEqual("#FYGC-MF89-XH2", GPC.encode(-12.1234567, -123.1234567))
        self.assertEqual((-12.12345, -123.12345), GPC.decode("#FYGC-MF89-XH2"))

    # Test latitude values outside valid range raise ValueError
    def test_latitude_out_of_range(self):
        for lat, lon in [(-90, -123), (90, 123)]:
            with self.assertRaises(ValueError) as cm:
                GPC.encode(lat, lon)
            self.assertEqual(str(cm.exception), "LATITUDE: value out of valid range.")

    # Test longitude values outside valid range raise ValueError
    def test_longitude_out_of_range(self):
        for lat, lon in [(-12, -180), (12, 180)]:
            with self.assertRaises(ValueError) as cm:
                GPC.encode(lat, lon)
            self.assertEqual(str(cm.exception), "LONGITUDE: value out of valid range.")

    # Test encoding/decoding with formatted GPC string (with hyphens and prefix)
    def test_gpc_formatted(self):
        self.assertEqual("#HG9P-JLHJ-X69", GPC.encode(-89.99999, -179.99999, True))
        self.assertEqual((-89.99999, -179.99999), GPC.decode("#HG9P-JLHJ-X69"))

    # Test encoding/decoding with unformatted GPC string (no hyphens or prefix)
    def test_gpc_unformatted(self):
        self.assertEqual("HG9PJLHJX69", GPC.encode(-89.99999, -179.99999, False))
        self.assertEqual((-89.99999, -179.99999), GPC.decode("HG9PJLHJX69"))

    # Test decoding of null, empty, or whitespace GPC strings raises ValueError
    def test_gpc_null(self):
        for code in [None, "", "    "]:
            with self.assertRaises(ValueError) as cm:
                GPC.decode(code)
            self.assertEqual(str(cm.exception), "GPC_NULL: Invalid GPC.")

    # Test decoding of GPC strings with invalid length raises ValueError
    def test_gpc_length(self):
        for code in ["#HG9P-JLHJ-X696", "#HG9P-JLHJ-X6"]:
            with self.assertRaises(ValueError) as cm:
                GPC.decode(code)
            self.assertEqual(str(cm.exception), "GPC_LENGTH: Invalid GPC.")

    # Test decoding of GPC strings with invalid characters raises ValueError
    def test_gpc_char(self):
        for code in ["#HG9P-JLHJ-A69", "#HG9P-JLHJ-E69"]:
            with self.assertRaises(ValueError) as cm:
                GPC.decode(code)
            self.assertEqual(str(cm.exception), "GPC_CHAR: Invalid GPC.")

    # Test decoding of GPC strings with characters outside valid range raises ValueError
    def test_gpc_range(self):
        for code in ["#HG9P-JLHJ-X7C", "#JG9P-JLHJ-X7C"]:
            with self.assertRaises(ValueError) as cm:
                GPC.decode(code)
            self.assertEqual(str(cm.exception), "GPC_RANGE: Invalid GPC.")

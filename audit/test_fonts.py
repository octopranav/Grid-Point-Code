"""Tests for the bundled-typeface check.

A font changed by a tool still renders, so the check is shown a changed font, a
missing one, an unpinned one and a missing licence, and has to name each.

    python -m unittest discover --start-directory audit --top-level-directory audit
"""

from __future__ import annotations

import shutil
import tempfile
import unittest
from pathlib import Path

from fonts import COVERS, FONTS, LICENCES, PINNED, blob, problems


class Blob(unittest.TestCase):
    def test_the_hash_is_the_one_git_gives(self):
        # `git hash-object` of an empty file, and of "hello\n".
        self.assertEqual(blob(b""), "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391")
        self.assertEqual(blob(b"hello\n"), "ce013625030ba8dba906f756967f9e9ca394464a")


class Bundled(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.fonts = self.root / "font"
        self.licences = self.root / "licences"
        shutil.copytree(FONTS, self.fonts)
        shutil.copytree(LICENCES, self.licences)

    def tearDown(self):
        shutil.rmtree(self.root)

    def test_the_repository_as_it_is_passes(self):
        self.assertEqual(problems(self.fonts, self.licences), [])

    def test_a_font_changed_by_one_byte_is_named(self):
        path = self.fonts / "ibm_plex_sans.ttf"
        data = bytearray(path.read_bytes())
        data[-1] ^= 1
        path.write_bytes(bytes(data))
        found = problems(self.fonts, self.licences)
        self.assertEqual(len(found), 1)
        self.assertIn("ibm_plex_sans.ttf is not the unmodified ofl/ibmplexsans/", found[0])

    def test_a_missing_font_is_named(self):
        (self.fonts / "bitter.ttf").unlink()
        self.assertEqual(problems(self.fonts, self.licences), [f"bitter.ttf is missing from {self.fonts.as_posix()}"])

    def test_a_font_nobody_pinned_is_named(self):
        (self.fonts / "extra.ttf").write_bytes(b"\0\1\0\0")
        self.assertEqual(problems(self.fonts, self.licences), ["extra.ttf is bundled but pinned to no upstream file"])

    def test_a_missing_licence_names_the_fonts_it_covered(self):
        (self.licences / "ibm-plex-OFL.txt").unlink()
        found = problems(self.fonts, self.licences)
        self.assertEqual(len(found), 1)
        self.assertIn("ibm_plex_mono_semibold.ttf would ship without its licence", found[0])

    def test_a_licence_that_lost_its_reserved_name_is_named(self):
        path = self.licences / "bitter-OFL.txt"
        path.write_text(path.read_text(encoding="utf-8").replace("Bitter Pro", "Bitter"), encoding="utf-8")
        self.assertEqual(problems(self.fonts, self.licences), ['bitter-OFL.txt no longer names its Reserved Font Name "Bitter Pro"'])

    def test_every_font_is_covered_by_a_licence(self):
        covered = {font for _, fonts in COVERS.values() for font in fonts}
        self.assertEqual(covered, set(PINNED))


if __name__ == "__main__":
    unittest.main()

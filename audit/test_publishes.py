"""Tests for the hidden-file deployment check.

This one guards a fault with no symptom: the build is green, the artifact is
built, the deployment succeeds, and one path is quietly missing from the server.
So the check is given workflows that break it, and a tree that proves what is at
stake.

    python -m unittest discover --start-directory audit --top-level-directory audit
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from publishes import hidden, keeps_hidden, packing


TOLD = """
      - name: Measure what will be published
        run: du -b web/dist

      - uses: actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9 # v5.0.0
        with:
          path: web/dist
          include-hidden-files: true

  deploy:
    name: Deploy
"""

SILENT = """
      - uses: actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9 # v5.0.0
        with:
          path: web/dist

  deploy:
    name: Deploy
"""

REFUSED = """
      - uses: actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9 # v5.0.0
        with:
          path: web/dist
          include-hidden-files: false
"""


class Packing(unittest.TestCase):
    def test_the_step_is_found_and_stops_at_the_next_one(self):
        step = packing(TOLD)
        self.assertIn("path: web/dist", step)
        self.assertNotIn("name: Deploy", step)

    def test_an_earlier_step_is_not_mistaken_for_it(self):
        self.assertNotIn("du -b", packing(TOLD))

    def test_a_workflow_without_the_step_reads_as_nothing(self):
        self.assertEqual(packing("on:\n  push:\n    branches: [main]\n"), "")


class Keeping(unittest.TestCase):
    def test_the_setting_is_read(self):
        self.assertTrue(keeps_hidden(TOLD))

    def test_the_default_is_a_failure(self):
        """The fault this exists for: saying nothing means dot files are dropped."""
        self.assertFalse(keeps_hidden(SILENT))

    def test_false_is_a_failure_too(self):
        self.assertFalse(keeps_hidden(REFUSED))

    def test_a_workflow_without_the_step_is_a_failure(self):
        self.assertFalse(keeps_hidden("jobs:\n  build:\n"))


class Hidden(unittest.TestCase):
    def test_a_dot_directory_is_named_once(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parent) as room:
            public = Path(room) / "public"
            (public / ".well-known").mkdir(parents=True)
            (public / ".well-known" / "assetlinks.json").write_text("[]", encoding="utf-8")
            (public / "favicon.svg").write_text("<svg />", encoding="utf-8")

            found = hidden(public)
            self.assertEqual(len(found), 1)
            self.assertTrue(found[0].endswith("public/.well-known"))

    def test_an_ordinary_file_is_not_reported(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parent) as room:
            public = Path(room) / "public"
            public.mkdir(parents=True)
            (public / "sw.js").write_text("// worker", encoding="utf-8")
            self.assertEqual(hidden(public), [])

    def test_a_directory_that_is_not_there_reads_as_empty(self):
        self.assertEqual(hidden(Path(__file__).resolve().parent / "nowhere"), [])


if __name__ == "__main__":
    unittest.main()

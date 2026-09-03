"""Tests for the deployment-trigger check.

A check nobody has watched fail is a check nobody knows works, and this one
guards a fault that is invisible by construction: the build stays green, the
site just stops being current. So it is given configurations that break it.

    python -m unittest discover --start-directory audit --top-level-directory audit
"""

from __future__ import annotations

import unittest

from deploys import collections, covered, triggers


CONFIG = """
const spec = defineCollection({
    loader: glob({ pattern: 'SPEC.md', base: '../' }),
});
const ports = defineCollection({
    loader: glob({ pattern: '{csharp,java}/README.md', base: '../' }),
});
const pages = defineCollection({
    loader: glob({ pattern: '**/*.md', base: './src/pages' }),
});
"""

WORKFLOW = """
on:
  push:
    branches: [main]
    paths:
      - 'web/**'
      - 'SPEC.md'
      - 'csharp/README.md'
      - 'java/README.md'
"""


class Collections(unittest.TestCase):
    def test_a_plain_pattern_is_found(self):
        self.assertIn('SPEC.md', collections(CONFIG))

    def test_braces_become_one_path_each(self):
        found = collections(CONFIG)
        self.assertIn('csharp/README.md', found)
        self.assertIn('java/README.md', found)

    def test_a_loader_inside_web_is_ignored(self):
        """`web/**` already covers it; repeating it would only go stale."""
        self.assertNotIn('**/*.md', collections(CONFIG))

    def test_nothing_is_found_in_an_empty_config(self):
        self.assertEqual(collections(''), [])


class Triggers(unittest.TestCase):
    def test_the_paths_list_is_read(self):
        self.assertEqual(
            triggers(WORKFLOW),
            ['web/**', 'SPEC.md', 'csharp/README.md', 'java/README.md'],
        )

    def test_a_workflow_with_no_paths_reads_as_none(self):
        self.assertEqual(triggers('on:\n  push:\n    branches: [main]\n'), [])


class Coverage(unittest.TestCase):
    def test_an_exact_path_is_covered(self):
        self.assertTrue(covered('SPEC.md', ['SPEC.md']))

    def test_a_double_star_covers_what_is_under_it(self):
        self.assertTrue(covered('web/src/pages/index.astro', ['web/**']))

    def test_a_double_star_does_not_cover_a_sibling(self):
        """The fault that shipped: `web/**` does not reach `SPEC.md`."""
        self.assertFalse(covered('SPEC.md', ['web/**', 'design/**']))

    def test_a_double_star_does_not_cover_a_prefix_collision(self):
        """`web/**` must not be read as covering `website.md`."""
        self.assertFalse(covered('website.md', ['web/**']))

    def test_a_missing_path_is_not_covered(self):
        self.assertFalse(covered('python/README.md', triggers(WORKFLOW)))

    def test_the_sample_pair_leaves_exactly_one_gap(self):
        """End to end on the fixtures: braces expand, and one is missing."""
        wanted = collections(CONFIG)
        have = triggers(WORKFLOW)
        missing = [p for p in wanted if not covered(p, have)]
        self.assertEqual(missing, [])


if __name__ == '__main__':
    unittest.main()

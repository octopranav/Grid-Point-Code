"""Tests for the documentation checker.

A checker nobody has watched fail is a checker nobody knows works. Every check
in `markdown.py` is given a document that breaks it and a document that does
not, because a check that silently passes everything looks exactly like a clean
repository.

    python -m unittest discover --start-directory audit --top-level-directory audit
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from markdown import (
    anchor,
    check_links,
    check_setext_traps,
    check_tables,
    headings,
    outside_fences,
)


def lines(text: str) -> list[str]:
    return text.strip('\n').split('\n')


class Anchors(unittest.TestCase):
    """The slug has to be GitHub's, or every fragment check is nonsense."""

    def slug(self, text: str) -> str:
        return anchor(text, {})

    def test_lowercases_and_hyphenates(self):
        self.assertEqual(self.slug('The Short Form'), 'the-short-form')

    def test_keeps_numbers_and_drops_punctuation(self):
        self.assertEqual(self.slug('5.4 Presentation'), '54-presentation')
        self.assertEqual(
            self.slug('14. The check character (optional)'),
            '14-the-check-character-optional',
        )

    def test_strips_inline_code_backticks_but_keeps_the_word(self):
        self.assertEqual(self.slug('The `#` marker'), 'the--marker')

    def test_numbers_duplicates_the_way_github_does(self):
        seen: dict[str, int] = {}
        self.assertEqual(anchor('Notes', seen), 'notes')
        self.assertEqual(anchor('Notes', seen), 'notes-1')
        self.assertEqual(anchor('Notes', seen), 'notes-2')


class Fences(unittest.TestCase):
    """A `#` inside a code block is a comment, not a heading."""

    def test_a_comment_in_a_python_block_is_not_a_heading(self):
        document = lines(
            '# Real heading\n'
            '\n'
            '```python\n'
            '# This is a comment, not a heading\n'
            'GPC.encode(1, 2)\n'
            '```\n'
            '\n'
            '## Another real one\n'
        )
        found = [text for _, _, text, _ in headings(document)]
        self.assertEqual(found, ['Real heading', 'Another real one'])

    def test_tildes_close_only_tildes(self):
        document = lines(
            '~~~\n'
            '# not a heading\n'
            '```\n'
            '# still not a heading\n'
            '~~~\n'
            '\n'
            '# a heading\n'
        )
        found = [text for _, _, text, _ in headings(document)]
        self.assertEqual(found, ['a heading'])

    def test_lines_inside_a_fence_are_not_offered(self):
        document = lines('outside\n```\ninside\n```\noutside again\n')
        self.assertEqual(
            [text for _, text in outside_fences(document)],
            ['outside', 'outside again'],
        )


class SetextTraps(unittest.TestCase):
    """Three dashes under text is a heading underline and wins silently."""

    def test_dashes_glued_to_a_paragraph_are_reported(self):
        problems: list[str] = []
        check_setext_traps(
            Path('x.md'), lines('Some paragraph text that ends here.\n---\n'), problems
        )
        self.assertEqual(len(problems), 1)
        self.assertIn('setext', problems[0])

    def test_a_rule_with_a_blank_line_above_it_is_fine(self):
        problems: list[str] = []
        check_setext_traps(
            Path('x.md'), lines('Some paragraph text that ends here.\n\n---\n'), problems
        )
        self.assertEqual(problems, [])

    def test_dashes_inside_a_fence_are_not_a_trap(self):
        problems: list[str] = []
        check_setext_traps(Path('x.md'), lines('```\nsome output\n---\n```\n'), problems)
        self.assertEqual(problems, [])


class Tables(unittest.TestCase):
    """A separator row that miscounts turns the table into a paragraph."""

    def test_a_mismatched_separator_is_reported(self):
        problems: list[str] = []
        check_tables(
            Path('x.md'), lines('| A | B | C |\n| --- | --- |\n| 1 | 2 | 3 |\n'), problems
        )
        self.assertEqual(len(problems), 1)
        self.assertIn('columns', problems[0])

    def test_a_matching_separator_is_fine(self):
        problems: list[str] = []
        check_tables(
            Path('x.md'),
            lines('| A | B | C |\n| --- | --- | --- |\n| 1 | 2 | 3 |\n'),
            problems,
        )
        self.assertEqual(problems, [])

    def test_alignment_colons_are_allowed(self):
        problems: list[str] = []
        check_tables(
            Path('x.md'), lines('| A | B |\n| :---: | ---: |\n| 1 | 2 |\n'), problems
        )
        self.assertEqual(problems, [])


class Links(unittest.TestCase):
    """Fragments must name a heading; relative paths must name a file."""

    def check(self, body: str, alongside: dict[str, str] | None = None) -> list[str]:
        with tempfile.TemporaryDirectory() as where:
            root = Path(where)
            page = root / 'page.md'
            page.write_text(body.strip('\n'), encoding='utf-8')
            for name, text in (alongside or {}).items():
                (root / name).write_text(text.strip('\n'), encoding='utf-8')

            document = lines(body)
            anchors = {page: {slug for _, _, _, slug in headings(document)}}
            problems: list[str] = []
            check_links(page, document, anchors, problems)
            return problems

    def test_a_fragment_with_no_heading_is_reported(self):
        problems = self.check('# Real heading\n\nSee [elsewhere](#no-such-heading).\n')
        self.assertEqual(len(problems), 1)
        self.assertIn('matches no heading', problems[0])

    def test_a_fragment_that_exists_is_fine(self):
        self.assertEqual(
            self.check('## The short form\n\nBack to [it](#the-short-form).\n'), []
        )

    def test_a_missing_file_is_reported(self):
        problems = self.check('See [the reference](reference/gone.py).\n')
        self.assertEqual(len(problems), 1)
        self.assertIn('does not exist', problems[0])

    def test_a_file_that_exists_is_fine(self):
        self.assertEqual(
            self.check('See [notes](notes.md).\n', {'notes.md': '# Notes'}), []
        )

    def test_a_fragment_in_another_file_is_checked(self):
        problems = self.check('See [notes](notes.md#missing).\n', {'notes.md': '# Notes'})
        self.assertEqual(len(problems), 1)
        self.assertIn('no such heading', problems[0])

    def test_a_fragment_in_another_file_that_exists_is_fine(self):
        self.assertEqual(
            self.check('See [notes](notes.md#notes).\n', {'notes.md': '# Notes'}), []
        )

    def test_external_links_are_left_alone(self):
        self.assertEqual(
            self.check('See [site](https://gridpointcode.com) and [mail](mailto:a@b.c).\n'),
            [],
        )

    def test_links_inside_a_fence_are_not_checked(self):
        self.assertEqual(self.check('```\n[not a link](nowhere.md)\n```\n'), [])


if __name__ == '__main__':
    unittest.main()

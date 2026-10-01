"""Tests for the check on what the site says about the Android app.

A wrong statement fails without a sound: the link opens in the browser, and the
device remembers. So each way of getting it wrong is handed to the check.

    python -m unittest discover --start-directory audit --top-level-directory audit
"""

from __future__ import annotations

import unittest

from assetlinks import application_id, asks_to_verify, problems

PRINT = ":".join(["14", "6D", "E9", "B8"] * 8)


def statement(**changes: object) -> dict:
    target = {"namespace": "android_app", "package_name": "com.gridpointcode", "sha256_cert_fingerprints": [PRINT]}
    target.update(changes)
    return {"relation": ["delegate_permission/common.handle_all_urls"], "target": target}


FILTER = """
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="gridpointcode.com" android:pathPrefix="/play" />
            </intent-filter>
"""


class AssetLinksTest(unittest.TestCase):
    def test_a_correct_statement_passes(self):
        self.assertEqual(problems([statement()], "com.gridpointcode"), [])

    def test_nothing_published_yet_passes(self):
        self.assertEqual(problems([], "com.gridpointcode"), [])

    def test_a_fingerprint_in_lower_case_fails(self):
        self.assertEqual(len(problems([statement(sha256_cert_fingerprints=[PRINT.lower()])], "com.gridpointcode")), 1)

    def test_a_fingerprint_without_colons_fails(self):
        self.assertEqual(len(problems([statement(sha256_cert_fingerprints=[PRINT.replace(":", "")])], "com.gridpointcode")), 1)

    def test_a_sha1_fingerprint_fails(self):
        self.assertEqual(len(problems([statement(sha256_cert_fingerprints=[":".join(["AB"] * 20)])], "com.gridpointcode")), 1)

    def test_a_statement_with_no_fingerprint_fails(self):
        self.assertEqual(len(problems([statement(sha256_cert_fingerprints=[])], "com.gridpointcode")), 1)

    def test_the_same_fingerprint_twice_fails(self):
        self.assertEqual(len(problems([statement(sha256_cert_fingerprints=[PRINT, PRINT])], "com.gridpointcode")), 1)

    def test_another_package_fails(self):
        found = problems([statement(package_name="com.gridpointcode.debug")], "com.gridpointcode")
        self.assertEqual(len(found), 1)
        self.assertIn("com.gridpointcode.debug", found[0])

    def test_a_relation_from_memory_fails(self):
        wrong = statement()
        wrong["relation"] = ["delegate_permission/common.handle_all_url"]
        self.assertEqual(len(problems([wrong], "com.gridpointcode")), 1)

    def test_a_web_target_fails(self):
        self.assertEqual(len(problems([statement(namespace="web")], "com.gridpointcode")), 1)

    def test_an_object_instead_of_a_list_fails(self):
        self.assertEqual(len(problems(statement(), "com.gridpointcode")), 1)

    def test_the_package_comes_from_the_build(self):
        self.assertEqual(application_id('        applicationId = "com.gridpointcode"\n'), "com.gridpointcode")
        self.assertIsNone(application_id("namespace = \"com.gridpointcode\"\n"))

    def test_the_manifest_must_ask(self):
        self.assertTrue(asks_to_verify(FILTER))
        self.assertFalse(asks_to_verify(FILTER.replace(' android:autoVerify="true"', "")))
        self.assertFalse(asks_to_verify(FILTER.replace('android:scheme="https"', 'android:scheme="geo"')))
        self.assertFalse(asks_to_verify(FILTER.replace("gridpointcode.com", "example.com")))


if __name__ == "__main__":
    unittest.main()

"""Tests for the Releases tab's pure logic: manifest checking and chunking."""

from __future__ import annotations

import hashlib
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from textpic_console.releases import (  # noqa: E402
    CHUNK_SIZE,
    Manifest,
    adoption_text,
    chunk_ranges,
    chunk_sha256,
    read_manifest,
    sha256_of_file,
    status_label,
    verify_manifest,
)


class ManifestTest(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.mkdtemp()
        self.apk_path = os.path.join(self.directory, "TextPic-1.1.4-release.apk")
        with open(self.apk_path, "wb") as handle:
            handle.write(b"apk-bytes" * 1000)
        self.sha = sha256_of_file(self.apk_path)
        self.size = os.path.getsize(self.apk_path)

    def write_manifest(self, **overrides) -> str:
        data = {
            "packageName": "com.textpic.studio",
            "versionCode": 9,
            "versionName": "1.1.4",
            "sha256": self.sha,
            "sizeBytes": self.size,
            "minSdk": 26,
            "builtAt": "2026-01-01T00:00:00Z",
        }
        data.update(overrides)
        path = os.path.join(self.directory, "release-manifest.json")
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(data, handle)
        return path

    def test_reads_a_good_manifest(self) -> None:
        manifest = read_manifest(self.write_manifest())
        self.assertEqual(manifest.version_code, 9)
        self.assertEqual(manifest.version_name, "1.1.4")
        self.assertEqual(manifest.size_bytes, self.size)

    def test_a_missing_file_is_a_clear_error(self) -> None:
        with self.assertRaises(ValueError):
            read_manifest(os.path.join(self.directory, "nope.json"))

    def test_bad_json_is_a_clear_error(self) -> None:
        path = os.path.join(self.directory, "broken.json")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write("{not json")
        with self.assertRaises(ValueError):
            read_manifest(path)

    def test_a_manifest_without_a_hash_is_refused(self) -> None:
        with self.assertRaises(ValueError):
            read_manifest(self.write_manifest(sha256="abc"))

    def test_verification_passes_for_the_matching_apk(self) -> None:
        manifest = read_manifest(self.write_manifest())
        verify_manifest(manifest, self.apk_path)

    def test_a_different_apk_is_refused(self) -> None:
        manifest = read_manifest(self.write_manifest())
        with open(self.apk_path, "ab") as handle:
            handle.write(b"tampered")
        with self.assertRaises(ValueError):
            verify_manifest(manifest, self.apk_path)

    def test_a_wrong_size_is_refused_before_hashing(self) -> None:
        manifest = Manifest(
            package_name="com.textpic.studio",
            version_code=9,
            version_name="1.1.4",
            sha256=self.sha,
            size_bytes=self.size + 1,
            min_sdk=26,
            built_at=None,
        )
        with self.assertRaises(ValueError) as caught:
            verify_manifest(manifest, self.apk_path)
        self.assertIn("bytes", str(caught.exception))


class ChunkTest(unittest.TestCase):
    def test_small_files_are_one_chunk(self) -> None:
        self.assertEqual(chunk_ranges(1_000), [(0, 1_000)])

    def test_exact_multiple_has_no_empty_tail(self) -> None:
        self.assertEqual(chunk_ranges(CHUNK_SIZE * 2), [(0, CHUNK_SIZE), (CHUNK_SIZE, CHUNK_SIZE)])

    def test_odd_size_covers_every_byte(self) -> None:
        size = CHUNK_SIZE * 2 + 17
        ranges = chunk_ranges(size)
        self.assertEqual(len(ranges), 3)
        self.assertEqual(ranges[-1], (CHUNK_SIZE * 2, 17))
        self.assertEqual(sum(length for _, length in ranges), size)

    def test_chunk_hash_matches_hashlib(self) -> None:
        data = b"hello chunk"
        self.assertEqual(chunk_sha256(data), hashlib.sha256(data).hexdigest())


class LabelsTest(unittest.TestCase):
    def test_status_labels(self) -> None:
        self.assertEqual(status_label("draft"), "Draft")
        self.assertEqual(status_label("published"), "Published")
        self.assertEqual(status_label("unpublished"), "Unpublished")

    def test_adoption_text(self) -> None:
        self.assertEqual(
            adoption_text({"onVersion": 5}, {"total": 8}),
            "5 of 8 customers",
        )


if __name__ == "__main__":
    unittest.main()

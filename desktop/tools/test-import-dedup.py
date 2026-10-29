"""Content deduplication and trustworthy import receipts, without user data."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from content_identity import verified_content
from media_import import MediaImporter


class ImportDedupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.importer = MediaImporter(self.directory)

    def upload(self, name, content):
        key = self.importer.begin(name, len(content))['import_id']
        self.importer.append_bytes(key, 0, content)
        return self.importer.finish(key)

    def test_renamed_identical_content_reuses_original(self):
        first = self.upload('original.png', b'original bytes')
        second = self.upload('renamed.jpg', b'original bytes')
        self.assertEqual(first['id'], second['id'])
        self.assertTrue(second['reused'])
        self.assertEqual(second['mime'], 'image/png')
        self.assertEqual(len(list(self.directory.iterdir())), 1)
        self.assertEqual(self.importer.completed_item(second['id']), second)

    def test_same_name_with_different_bytes_is_distinct(self):
        first = self.upload('same.png', b'aaaa')
        second = self.upload('same.png', b'bbbb')
        self.assertNotEqual(first['id'], second['id'])
        self.assertFalse(second['reused'])

    def test_existing_original_is_reused_and_never_renamed(self):
        original = self.directory / ('a'*32+'.png')
        original.write_bytes(b'keep me')
        item = self.upload('new.png', b'keep me')
        self.assertEqual(item['id'], original.name)
        self.assertEqual(original.read_bytes(), b'keep me')

    def test_cached_hash_is_invalidated_by_content_change(self):
        item = self.upload('old.png', b'aaaa')
        path = self.directory / item['id']
        verified_content(path)
        old = path.stat()
        path.write_bytes(b'bbbb')
        os.utime(path, ns=(old.st_atime_ns, old.st_mtime_ns))
        self.assertEqual(verified_content(path)[1], hashlib.sha256(b'bbbb').hexdigest())
        with self.assertRaises(ValueError):
            self.importer.completed_item(item['id'])

    def test_parallel_finishes_cannot_create_duplicate_files(self):
        keys = [self.importer.begin('same.png', 4)['import_id'] for _ in range(2)]
        for key in keys:
            self.importer.append_bytes(key, 0, b'same')
        with ThreadPoolExecutor(max_workers=2) as executor:
            items = list(executor.map(self.importer.finish, keys))
        self.assertEqual(items[0]['id'], items[1]['id'])
        self.assertEqual(len(list(self.directory.iterdir())), 1)

    def test_incomplete_copy_has_no_receipt(self):
        key = self.importer.begin('half.png', 8)['import_id']
        self.importer.append_bytes(key, 0, b'half')
        with self.assertRaises(ValueError):
            self.importer.finish(key)
        with self.assertRaises(ValueError):
            self.importer.completed_item(key)
        self.importer.abort(key)
        self.assertEqual(list(self.directory.iterdir()), [])

    def test_forged_receipt_rejected(self):
        (self.directory / 'outside.png').write_bytes(b'private')
        with self.assertRaises(ValueError):
            self.importer.completed_item('outside.png')

    def test_clipboard_and_file_picker_share_content_dedup(self):
        original = self.directory.parent / (self.directory.name+'-picked.png')
        original.write_bytes(b'picked content')
        try:
            picked = self.importer.copy(original)
            pasted = self.importer.import_bytes('clipboard.png', b'picked content')
            self.assertEqual(picked['id'], pasted['id'])
            self.assertTrue(pasted['reused'])
            self.assertEqual(original.read_bytes(), b'picked content')
        finally:
            original.unlink()

    def test_alias_target_is_reused_without_an_extra_physical_copy(self):
        # Keep alias metadata in the isolated profile rather than shared Temp.
        media = self.directory/'media';media.mkdir()
        original = media/'legacy-original.jpeg';original.write_bytes(b'legacy bytes')
        alias = 'b'*32+'.jpeg'
        (self.directory/'media-aliases.json').write_text(json.dumps({alias:original.name}), 'utf-8')
        importer = MediaImporter(media)
        item = importer.import_bytes('renamed.jpg', b'legacy bytes')
        self.assertTrue(item['reused'])
        self.assertEqual(item['id'], alias)
        self.assertEqual(importer.completed_item(alias), item)
        self.assertEqual(list(media.iterdir()), [original])


if __name__ == '__main__':
    unittest.main()

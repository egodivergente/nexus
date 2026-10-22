import unittest

import flow_gallery
import recovery


class GalleryTests(unittest.TestCase):
    def setUp(self):
        self.url = 'https://flow.google.com/project/project-1234'
        self.old = {'media_id': 'old-media-1234', 'kind': 'image'}
        self.new = {'media_id': 'new-media-5678', 'kind': 'image'}

    def test_only_new_media_counts_and_scope_is_not_an_output(self):
        baseline = flow_gallery.controls(self.url, [self.old])
        current = flow_gallery.controls(self.url, [self.new, self.old])
        additions, error = recovery.new_downloads(
            {'baseline_downloads': [c['signature'] for c in baseline]}, current)
        self.assertIsNone(error)
        self.assertEqual([c['media_id'] for c in additions], [self.new['media_id']])
        # Old tiles can move without invalidating their identity.
        self.assertTrue(recovery.download_control_matches(baseline[1], current))

    def test_project_switch_and_missing_baseline_are_not_new_results(self):
        baseline = flow_gallery.controls(self.url, [self.old])
        job = {'baseline_downloads': [c['signature'] for c in baseline]}
        for current in [flow_gallery.controls(self.url + '-different', [self.new]),
                        flow_gallery.controls(self.url, [self.new])]:
            self.assertEqual((None, 'download_controls_changed'), recovery.new_downloads(job, current))
        self.assertEqual((None, 'download_controls_changed'), recovery.new_downloads(
            {'baseline_downloads': []}, flow_gallery.controls(self.url, [self.old])))

    def test_empty_project_can_receive_first_result_without_returning_old_tiles(self):
        baseline = flow_gallery.controls(self.url, [])
        additions, error = recovery.new_downloads(
            {'baseline_downloads': [c['signature'] for c in baseline]}, flow_gallery.controls(self.url, [self.new]))
        self.assertIsNone(error)
        self.assertEqual(1, len(additions))
        self.assertEqual(self.new['media_id'], additions[0]['media_id'])

    def test_complete_inventory_allows_old_tiles_to_unmount_without_counting_them_as_new(self):
        baseline=flow_gallery.controls(self.url,[self.old])
        job={'baseline_downloads':[c['signature'] for c in baseline],'baseline_complete':True}
        added,error=recovery.new_downloads(job,flow_gallery.controls(self.url,[self.new]))
        self.assertIsNone(error)
        self.assertEqual([self.new['media_id']],[c['media_id'] for c in added])
        self.assertEqual((None,'download_controls_changed'),recovery.new_downloads(job,
            flow_gallery.controls(self.url+'-different',[self.new])))

    def test_duplicate_tiles_and_unsafe_ids_are_not_downloaded_twice(self):
        items = flow_gallery.controls(self.url, [self.old, self.old, {'media_id': '"] evil'}])
        self.assertEqual(2, len(items))  # Project scope plus one result.
        self.assertEqual([], flow_gallery.controls('https://other.example/project/project-1234', [self.old]))


if __name__ == '__main__':
    unittest.main()

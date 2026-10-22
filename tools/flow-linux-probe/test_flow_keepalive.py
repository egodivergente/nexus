import unittest

from flow_keepalive import (PARK_AFTER, RELOGIN_AFTER, RELOGIN_COOLDOWN, REOPEN_AFTER, classify, decide,
                            login_outcome)

PROJECT = 'https://flow.google.com/project/fixture-project'


class Classify(unittest.TestCase):
    def test_project_wins(self):
        self.assertEqual(classify(['https://flow.google.com/', PROJECT]), ('project', PROJECT))

    def test_login_in_progress_is_not_signed_out(self):
        urls = ['https://flow.google.com/about', 'https://accounts.google.com/v3/signin/identifier']
        self.assertEqual(classify(urls)[0], 'login')

    def test_page_kinds(self):
        self.assertEqual(classify(['https://flow.google.com/about'])[0], 'signed_out')
        self.assertEqual(classify(['https://flow.google.com/'])[0], 'gallery')
        self.assertEqual(classify(['about:blank', 'chrome://newtab/'])[0], 'blank')

    def test_lookalike_host_is_ignored(self):
        self.assertEqual(classify(['https://flow.google.com.evil.test/project/x'])[0], 'blank')


class Decide(unittest.TestCase):
    def test_project_is_remembered(self):
        action, state = decide('project', PROJECT, {}, 0)
        self.assertIsNone(action)
        self.assertEqual(state['last_project'], PROJECT)

    def test_gallery_reopens_only_after_grace(self):
        _, state = decide('project', PROJECT, {}, 0)
        action, state = decide('gallery', None, state, 100)
        self.assertIsNone(action)
        action, state = decide('gallery', None, state, 100 + REOPEN_AFTER)
        self.assertEqual(action, 'reopen')
        # The next reopen waits a full grace period again.
        action, _ = decide('gallery', None, state, 101 + REOPEN_AFTER)
        self.assertIsNone(action)

    def test_gallery_without_known_project_does_nothing(self):
        action, _ = decide('gallery', None, {'kind': 'gallery', 'since': 0}, 10 * REOPEN_AFTER)
        self.assertIsNone(action)

    def test_signed_out_tries_one_relogin_then_parks(self):
        _, state = decide('project', PROJECT, {}, 0)
        action, state = decide('signed_out', None, state, 10)
        self.assertIsNone(action)
        action, state = decide('signed_out', None, state, 10 + RELOGIN_AFTER)
        self.assertEqual(action, 'relogin')
        # A failed attempt is not repeated within the cooldown; the page is parked instead.
        action, state = decide('signed_out', None, state, 20 + RELOGIN_AFTER)
        self.assertIsNone(action)
        action, state = decide('signed_out', None, state, 10 + PARK_AFTER)
        self.assertEqual(action, 'park')
        action, state = decide('signed_out', None, state, 20 + PARK_AFTER)
        self.assertIsNone(action)
        # Parked blank page is not reopened as a project: that would land on /about again.
        action, state = decide('blank', None, state, 30 + PARK_AFTER)
        self.assertIsNone(action)
        # After the cooldown the page is woken for exactly one more attempt.
        later = max(30 + PARK_AFTER, state['last_relogin']) + RELOGIN_COOLDOWN
        action, state = decide('blank', None, state, later)
        self.assertEqual(action, 'wake')
        action, state = decide('signed_out', None, state, later + 300)
        self.assertIsNone(action)
        action, state = decide('signed_out', None, state, later + 300 + RELOGIN_AFTER)
        self.assertEqual(action, 'relogin')

    def test_sign_in_clears_parking_and_project_is_reopened(self):
        state = {'kind': 'blank', 'since': 0, 'parked': True, 'last_project': PROJECT}
        action, state = decide('login', None, state, 10)
        self.assertIsNone(action)
        self.assertFalse(state['parked'])
        _, state = decide('gallery', None, state, 20)
        action, _ = decide('gallery', None, state, 20 + REOPEN_AFTER)
        self.assertEqual(action, 'reopen')

    def test_stalled_login_gets_one_bounded_saved_signin_attempt(self):
        state = {'kind': 'login', 'since': 0, 'last_project': PROJECT}
        action, _ = decide('login', None, state, 100 * PARK_AFTER)
        self.assertEqual(action, 'relogin')


class LoginOutcome(unittest.TestCase):
    def test_outcomes(self):
        self.assertEqual(login_outcome(PROJECT), 'signed_in')
        self.assertEqual(login_outcome('https://flow.google.com/'), 'signed_in')
        self.assertEqual(login_outcome('https://flow.google.com/about'), 'unexpected_destination')
        self.assertIsNone(login_outcome('https://accounts.google.com/v3/signin/challenge/pwd?x=1'))
        self.assertIsNone(login_outcome('https://accounts.google.com/v3/signin/accountchooser'))
        self.assertEqual(login_outcome('https://accounts.google.com/v3/signin/challenge/ipp'), 'verification_required')
        self.assertEqual(login_outcome('https://accounts.google.com/signin/rejected'), 'browser_rejected')
        self.assertEqual(login_outcome('https://accounts.google.com/signin/oauth/consent'), 'consent_required')
        self.assertEqual(login_outcome('https://accounts.google.com.evil.test/'), 'unexpected_destination')


if __name__ == '__main__':
    unittest.main()

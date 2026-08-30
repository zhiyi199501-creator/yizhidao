import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient


class LegalLanguageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from app.main import app

        cls.client = TestClient(app)

    def test_privacy_default_is_chinese(self):
        response = self.client.get("/privacy")
        self.assertEqual(response.status_code, 200)
        self.assertIn("隐私政策", response.text)
        self.assertNotIn("Privacy Policy", response.text)

    def test_privacy_english_query(self):
        response = self.client.get("/privacy", params={"lang": "en"})
        self.assertEqual(response.status_code, 200)
        self.assertIn("Privacy Policy", response.text)
        self.assertIn("Yiwanjia", response.text)

    def test_privacy_accept_language(self):
        response = self.client.get("/privacy", headers={"Accept-Language": "en-US,en;q=0.9"})
        self.assertEqual(response.status_code, 200)
        self.assertIn("Privacy Policy", response.text)

    def test_terms_and_support_english(self):
        terms = self.client.get("/terms", params={"lang": "en"})
        support = self.client.get("/support", params={"lang": "en"})
        self.assertIn("Terms of Use", terms.text)
        self.assertIn("Support", support.text)
        self.assertIn("/privacy?lang=en", support.text)


if __name__ == "__main__":
    unittest.main()

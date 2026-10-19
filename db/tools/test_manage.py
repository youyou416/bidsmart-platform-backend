"""Offline migration-control tests; no database connectivity or SQL compatibility claim."""
import contextlib
import io
import os
import unittest
from unittest.mock import Mock, patch
import manage


class CaptchaMigrationTest(unittest.TestCase):
    def run_upgrade(self, mode, states):
        db = Mock()
        db.query.return_value = [["hr_score_dev", "synthetic-version", "OceanBase", "utf8mb4", "utf8mb4_bin", "+00:00"]]
        arguments = ["manage.py", mode, "--version", "V002"]
        if mode == "init":
            arguments += ["--confirm-target", "hr_score_dev", "--confirmed-mysql-tenant", "sys"]
        with patch.dict(os.environ, {"DB_PASSWORD": "synthetic-test-only", "DB_HOST": "127.0.0.1", "DB_USERNAME": "root@sys", "APP_ENV": "dev"}, clear=True), \
                patch("sys.argv", arguments), patch.object(manage.shutil, "which", return_value="synthetic-mysql"), \
                patch.object(manage, "Database", return_value=db), patch.object(manage, "inspect", side_effect=states), \
                patch.object(manage, "inspect_seed") as seed, contextlib.redirect_stdout(io.StringIO()):
            result = manage.main()
        seed.assert_not_called()
        return result, db

    def test_missing_table_check_is_read_only(self):
        result, db = self.run_upgrade("check", ["present", "absent"])
        self.assertEqual(result, 2)
        db.execute_file.assert_not_called()

    def test_upgrade_creates_only_v002_and_does_not_seed_or_modify_accounts(self):
        result, db = self.run_upgrade("init", ["present", "absent", "present"])
        self.assertEqual(result, 0)
        db.execute_file.assert_called_once_with(manage.CAPTCHA_SCHEMA)

    def test_repeated_upgrade_of_compatible_table_does_not_write(self):
        result, db = self.run_upgrade("init", ["present", "present"])
        self.assertEqual(result, 0)
        db.execute_file.assert_not_called()

    def test_v001_must_exist_before_upgrade(self):
        with self.assertRaisesRegex(RuntimeError, "V001"):
            self.run_upgrade("init", ["absent"])

    def test_structure_conflict_stops_upgrade(self):
        with self.assertRaisesRegex(RuntimeError, "conflict"):
            self.run_upgrade("init", ["present", RuntimeError("schema conflict")])

    def test_versioned_schema_parser_preserves_base_and_new_constraints(self):
        self.assertEqual(len(manage.expected_schema()), 11)
        schema = manage.expected_schema(manage.CAPTCHA_SCHEMA, 1)
        self.assertEqual(set(schema), {"sys_login_captcha"})
        columns, indexes = schema["sys_login_captcha"]
        self.assertEqual([column[0] for column in columns], ["captcha_id", "session_binding_hash", "answer_hash", "created_at", "expires_at"])
        self.assertEqual(indexes["uk_captcha_session"], (["session_binding_hash"], True))
        self.assertEqual(indexes["idx_captcha_expires"], (["expires_at"], False))


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Explicit, conflict-detecting OceanBase framework initialization.

This utility never creates a database/tenant, changes an existing password,
or alters/drops a table. It requires the MySQL 8 command-line client.
"""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

BASE = Path(__file__).resolve().parents[1]
SCHEMA = BASE / "migrations/V001__framework_schema.sql"
CAPTCHA_SCHEMA = BASE / "migrations/V002__login_captcha.sql"
SEED = BASE / "migrations/V001__synthetic_seed.sql"
TARGET = "hr_score_dev"


def expected_schema(path=SCHEMA, count=11):
    result = {}
    for table, body in re.findall(r"CREATE TABLE (\w+) \((.*?)\) DEFAULT", path.read_text(), re.S):
        columns = []
        for line in body.splitlines():
            match = re.match(r"\s*(\w+) (BIGINT|VARCHAR|DATETIME|TINYINT|INT)(?:\((\d+)\))?( UNSIGNED)? (.*?)(?:,)?$", line)
            if match:
                name, kind, width, unsigned, tail = match.groups()
                columns.append((name, kind.lower(), width or "", bool(unsigned), "NO" if "NOT NULL" in tail else "YES"))
        primary = re.search(r"PRIMARY KEY \((.*?)\)", body)
        indexes = {"PRIMARY": ([item.strip() for item in primary.group(1).split(",")], True)}
        for unique, index, fields in re.findall(r"(UNIQUE )?KEY (\w+) \((.*?)\)", body):
            indexes[index] = ([item.strip() for item in fields.split(",")], bool(unique))
        result[table] = (columns, indexes)
    if len(result) != count:
        raise RuntimeError("Unexpected number of versioned tables.")
    return result


def option_value(value):
    # MySQL option-file escaping. Never place passwords on the command line.
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\r", "\\r") + '"'


class Database:
    def __init__(self, config_path):
        self.command = ["mysql", "--defaults-file=" + str(config_path), "--batch", "--raw", "--skip-column-names", "--connect-timeout=5"]

    def query(self, sql):
        completed = subprocess.run(self.command, input="SET time_zone='+00:00';\n" + sql, text=True, capture_output=True, timeout=60)
        if completed.returncode:
            # Do not echo connector diagnostics, SQL or environment values.
            raise RuntimeError("Database command failed. Check target permissions, SQL compatibility, network and TLS with a trusted administrator; no settings were weakened.")
        return [line.split("\t") for line in completed.stdout.splitlines() if line]

    def execute_file(self, path):
        self.query(path.read_text(encoding="utf-8"))


def inspect(db, schema):
    names = ",".join("'" + name + "'" for name in schema)
    tables = {row[0] for row in db.query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (" + names + ");")}
    if not tables:
        return "absent"
    if tables != set(schema):
        raise RuntimeError("Only part of the selected migration exists. Automatic repair is forbidden; inspect the interrupted DDL and recover explicitly.")
    metadata = db.query("SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,COLUMN_TYPE,IS_NULLABLE,COALESCE(CHARACTER_MAXIMUM_LENGTH,''),COALESCE(DATETIME_PRECISION,'') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (" + names + ") ORDER BY TABLE_NAME,ORDINAL_POSITION;")
    grouped = {name: [] for name in schema}
    for table, name, kind, column_type, nullable, char_length, datetime_precision in metadata:
        width = char_length if kind == "varchar" else datetime_precision if kind == "datetime" else ""
        grouped[table].append((name, kind, width, "unsigned" in column_type.lower(), nullable))
    for table, (columns, indexes) in schema.items():
        if grouped[table] != columns:
            raise RuntimeError("Existing table structure conflicts with the selected migration: " + table + ". Nothing was overwritten.")
        actual_indexes = {}
        for index, column, non_unique in db.query("SELECT INDEX_NAME,COLUMN_NAME,NON_UNIQUE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + table + "' ORDER BY INDEX_NAME,SEQ_IN_INDEX;"):
            if index not in actual_indexes:
                actual_indexes[index] = ([], non_unique == "0")
            actual_indexes[index][0].append(column)
        if actual_indexes != indexes:
            raise RuntimeError("Existing indexes conflict with the selected migration: " + table)
    if "sys_login_log" in schema:
        auto_increment = db.query("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('sys_login_log','sys_operation_log') AND EXTRA LIKE '%auto_increment%' ORDER BY TABLE_NAME;")
        if auto_increment != [["sys_login_log", "id"], ["sys_operation_log", "id"]]:
            raise RuntimeError("Required audit-log auto-increment identifiers are missing or conflicting.")
    charsets = db.query("SELECT TABLE_NAME,TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (" + names + ");")
    if any(collation.lower() != "utf8mb4_bin" for _, collation in charsets):
        raise RuntimeError("Framework table collation differs from the reviewed utf8mb4_bin baseline.")
    return "present"


def inspect_seed(db):
    expected = [
        ("SELECT id,COALESCE(parent_id,'NULL'),name,status FROM sys_dept WHERE id=1001;", [["1001", "NULL", "合成根部门", "ENABLED"]]),
        ("SELECT id,COALESCE(parent_id,'NULL'),name,status FROM sys_role WHERE id IN (1001,1002) ORDER BY id;", [["1001", "NULL", "平台管理员", "ENABLED"], ["1002", "NULL", "相关部门", "ENABLED"]]),
        ("SELECT id,COALESCE(parent_id,'NULL'),name,resource_type,sort_order,route_name,route_path,component,icon,visible,status,permission_code FROM sys_resource WHERE id=1001;", [["1001", "NULL", "框架说明", "MENU", "10", "FrameworkHome", "/home", "Home", "House", "1", "ENABLED", "framework:home:view"]]),
        ("SELECT role_id,resource_id FROM sys_role_resource WHERE role_id=1001 AND resource_id=1001;", [["1001", "1001"]]),
    ]
    observed = [db.query(query) for query, _ in expected]
    if all(not rows for rows in observed):
        # An initialization must never silently grant rights in a populated DB.
        names = list(expected_schema())
        counts = db.query("SELECT " + "+".join("(SELECT COUNT(*) FROM " + name + ")" for name in names) + ";")
        if counts != [["0"]]:
            raise RuntimeError("Framework configuration is already populated. Explicit seed reconciliation is required; no grants were added.")
        return "absent"
    if observed != [rows for _, rows in expected]:
        raise RuntimeError("Synthetic seed IDs conflict with existing configuration or authorization. No row or grant was overwritten.")
    return "present"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["check", "init"], help="check is read-only; init is an explicit write")
    parser.add_argument("--version", choices=["V001", "V002"], default="V001", help="V002 adds the login captcha table after V001")
    parser.add_argument("--confirm-target", help="init requires literal hr_score_dev")
    parser.add_argument("--confirmed-mysql-tenant", help="init requires the tenant name verified independently in OceanBase administration")
    args = parser.parse_args()
    if not shutil.which("mysql"):
        raise RuntimeError("MySQL 8 command-line client is required; no database operation was attempted.")
    host = os.environ.get("DB_HOST", "127.0.0.1")
    port = os.environ.get("DB_PORT", "2881")
    username = os.environ.get("DB_USERNAME", "root@sys")
    database = os.environ.get("DB_NAME", TARGET)
    password = os.environ.get("DB_PASSWORD")
    if database != TARGET:
        raise RuntimeError("This reviewed migration is restricted to hr_score_dev; it does not initialize any other database.")
    if not password:
        raise RuntimeError("DB_PASSWORD is absent. Supply it locally; no default password exists.")
    system_tenant = "@sys" in username.lower() or "#sys" in username.lower()
    root_account = username.lower().split("@", 1)[0].split("#", 1)[0] == "root"
    if system_tenant and host not in ("127.0.0.1", "localhost", "::1"):
        raise RuntimeError("System-tenant credentials are restricted to isolated loopback development.")
    profile = os.environ.get("SPRING_PROFILES_ACTIVE", os.environ.get("APP_ENV", "dev"))
    if profile not in ("dev", "test", "prod") or (os.environ.get("SPRING_PROFILES_ACTIVE") and os.environ.get("APP_ENV") and os.environ["SPRING_PROFILES_ACTIVE"] != os.environ["APP_ENV"]):
        raise RuntimeError("Environment must be exactly dev, test or prod; APP_ENV and SPRING_PROFILES_ACTIVE may not disagree.")
    if profile == "test":
        raise RuntimeError("test requires an independent database and cannot use hr_score_dev. This migration utility is restricted to the reviewed development target.")
    if profile == "prod" and (system_tenant or root_account):
        raise RuntimeError("System-tenant/root credentials are forbidden in production.")
    tls_mode = os.environ.get("DB_SSL_MODE", "VERIFY_IDENTITY")
    if tls_mode not in ("VERIFY_IDENTITY", "VERIFY_CA", "DISABLED"):
        raise RuntimeError("DB_SSL_MODE must be VERIFY_IDENTITY, VERIFY_CA, or explicitly local DISABLED.")
    if profile == "prod" and tls_mode != "VERIFY_IDENTITY":
        raise RuntimeError("Production database identity verification is mandatory.")
    if tls_mode == "DISABLED" and (host not in ("127.0.0.1", "localhost", "::1") or profile != "dev"):
        raise RuntimeError("Plaintext DB access is only permitted for explicitly isolated loopback dev.")
    if args.mode == "init" and (args.confirm_target != TARGET or not args.confirmed_mysql_tenant):
        raise RuntimeError("init requires --confirm-target hr_score_dev and --confirmed-mysql-tenant after tenant/mode/ownership checks.")
    if args.mode == "init" and "@" in username and username.rsplit("@", 1)[1].split("#", 1)[0] != args.confirmed_mysql_tenant:
        raise RuntimeError("Confirmed tenant does not match the configured username; no write was attempted.")
    schema = expected_schema()
    with tempfile.TemporaryDirectory(prefix="bidsmart-db-") as directory:
        config = Path(directory) / "client.cnf"
        config.touch(mode=0o600)
        entries = {"host": host, "port": port, "user": username, "password": password, "database": database, "default-character-set": "utf8mb4", "ssl-mode": tls_mode}
        if os.environ.get("DB_SSL_CA"):
            entries["ssl-ca"] = os.environ["DB_SSL_CA"]
        config.write_text("[client]\n" + "\n".join(key + "=" + option_value(value) for key, value in entries.items()) + "\n", encoding="utf-8")
        db = Database(config)
        identity = db.query("SELECT DATABASE(),VERSION(),@@version_comment,@@character_set_database,@@collation_database,@@time_zone;")
        if len(identity) != 1 or len(identity[0]) != 6 or identity[0][0] != TARGET or "oceanbase" not in " ".join(identity[0][1:3]).lower():
            raise RuntimeError("Target database or OceanBase product identity could not be confirmed; no writes were attempted.")
        print("Read-only product/database evidence:", " | ".join(identity[0]))
        print("Tenant name, MySQL compatibility mode, schema ownership and privileges still require the administration checklist.")
        if args.version == "V002":
            if inspect(db, schema) != "present":
                raise RuntimeError("Initialize and verify V001 before applying V002.")
            captcha_schema = expected_schema(CAPTCHA_SCHEMA, 1)
            captcha_state = inspect(db, captcha_schema)
            if captcha_state == "absent":
                if args.mode == "check":
                    print("V002 captcha table is absent. No DDL was executed.")
                    return 2
                db.execute_file(CAPTCHA_SCHEMA)
                inspect(db, captcha_schema)
                print("Created V002 sys_login_captcha. No existing data or account was changed.")
            else:
                print("Compatible V002 captcha table already exists; no DDL was executed.")
            return 0
        state = inspect(db, schema)
        if state == "absent":
            if args.mode == "check":
                print("V001 framework tables are absent. No DDL was executed.")
                return 2
            db.execute_file(SCHEMA)
            inspect(db, schema)
            print("Created eleven V001 framework tables; no database or tenant was created.")
        seed = inspect_seed(db)
        if seed == "absent":
            if args.mode == "check":
                print("Synthetic configuration is absent. No data was inserted.")
                return 2
            db.execute_file(SEED)
            inspect_seed(db)
            print("Inserted synthetic department, independent roles, home resource and explicit grant.")
        else:
            print("Compatible V001 schema and synthetic configuration already exist; no password or grant was modified.")
        print("Initialization/check completed. No account was created. OceanBase integration test checklist remains separate.")
        return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, subprocess.TimeoutExpired, OSError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)

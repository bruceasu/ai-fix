"""
sys_common.py - Shared utility library for all checker modules.

Merged from sync_common.py (env loading, logging, psql-go helpers, Java app helpers)
and common checker logic (timezone, datetime validation, tool resolution, SQL building,
database query execution, S3 upload, generic checker execution).

All checker modules import from this single file instead of duplicating code.
"""
import os
import shlex
import shutil
import subprocess
import sys
from datetime import datetime, timezone, timedelta
from pathlib import Path
from types import ModuleType
from typing import Callable, Optional


# ============================================================
# Env file loading
# ============================================================

def load_env_file(path: Path) -> None:
    """Load KEY=VALUE pairs from env file into os.environ (like bash source).

    Supports: KEY=VALUE, KEY="VALUE", KEY='VALUE',
              export KEY=VALUE, export KEY="VALUE"
    Bash semantics: single-quoted values are literal (no expansion),
    double-quoted / unquoted values expand ~ and $VAR / ${VAR}.
    """
    if not path.is_file():
        return
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("export "):
                line = line[len("export "):].strip()
            if "=" not in line:
                continue
            key, _, value = line.partition("=")
            key = key.strip()
            if not key:
                continue
            value = value.strip()
            # Detect quote style before stripping
            if value.startswith("'") and value.endswith("'"):
                # Single-quoted: literal, no expansion (bash semantics)
                value = value[1:-1]
            else:
                # Double-quoted or unquoted: expand ~ and $VAR
                if value.startswith('"') and value.endswith('"'):
                    value = value[1:-1]
                value = os.path.expandvars(os.path.expanduser(value))
            os.environ[key] = value


def init_env() -> None:
    """Load standard env files: ~/.config/env and ./.env."""
    load_env_file(Path.home() / ".config" / "env")
    load_env_file(Path("./.env"))




def get_env(key: str, default: str = "") -> str:
    """Get environment variable with optional default."""
    return os.environ.get(key, default)


def set_env(key: str, value: str, expand: bool = False) -> None:
    """Set environment variable.

    If expand=True, expand ~ and $VAR/${VAR} in value before setting.
    """
    if expand:
        value = os.path.expandvars(os.path.expanduser(value))
    os.environ[key] = value


# ============================================================
# Logging
# ============================================================

def log(msg: str) -> None:
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"{ts} [INFO] {msg}", flush=True)


def error(msg: str) -> None:
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"{ts} [ERROR] {msg}", file=sys.stderr, flush=True)


# ============================================================
# Validation helpers
# ============================================================

def require_param(key: str) -> str:
    """Require an environment variable to be set and non-empty. Returns its value."""
    value = os.environ.get(key, "")
    if not value:
        error(f"missing required parameter: {key}")
        sys.exit(1)
    return value


def require_file(path: str) -> None:
    """Require a file to exist."""
    if not Path(path).is_file():
        error(f"file not found: {path}")
        sys.exit(1)


def require_cmd(name: str) -> None:
    """Require a command to be available on PATH."""
    if shutil.which(name) is None:
        error(f"command not found: {name}")
        sys.exit(1)


def path_munge(directory: str, after: bool = False) -> None:
    """Add directory to PATH if not already present."""
    current = os.environ.get("PATH", "")
    sep = ";" if sys.platform == "win32" else ":"
    dirs = current.split(sep)
    if directory not in dirs:
        if after:
            os.environ["PATH"] = current + sep + directory
        else:
            os.environ["PATH"] = directory + sep + current


# ============================================================
# psql-go helpers
# ============================================================

def resolve_psql(script_dir: Path) -> str:
    """Resolve psql-go executable path."""
    psql = os.environ.get("PSQL")
    if psql:
        return psql
    local = script_dir / ("psql-go.exe" if sys.platform == "win32" else "psql-go")
    if local.exists():
        return str(local)
    project_root = script_dir.parents[3]  # src/main/scripts -> project root
    fallback = project_root / "go" / "psql-go" / "bin" / ("psql-go.exe" if sys.platform == "win32" else "psql-go")
    if fallback.exists():
        return str(fallback)
    return str(local)


def psql_exec(psql_cmd: str, sql: str) -> None:
    """Execute SQL without capturing output."""
    result = subprocess.run([psql_cmd, "-c", sql])
    if result.returncode != 0:
        raise RuntimeError(f"psql-go failed with rc={result.returncode}")


def psql_query(psql_cmd: str, sql: str) -> str:
    """Execute SQL and return tuples-only output (trimmed)."""
    result = subprocess.run([psql_cmd, "-t", "-c", sql], capture_output=True, text=True)
    if result.returncode != 0:
        error(f"psql-go stderr: {result.stderr.strip()}")
        raise RuntimeError(f"psql-go failed with rc={result.returncode}")
    return result.stdout.strip()


# ============================================================
# Java app helpers
# ============================================================

def resolve_app_bin(script_dir: Path, *extra_env_keys: str) -> list[str]:
    """Resolve the Java app binary command.

    Checks APP_BIN first, then any extra env keys (e.g. SYNC_GX_EOD_POSITION_APP_BIN),
    falls back to java -jar <script_dir>/app.jar.
    """
    for key in ("APP_BIN", *extra_env_keys):
        val = os.environ.get(key)
        if val:
            return shlex.split(val)
    jar = script_dir / "app.jar"
    return ["java", "-jar", str(jar)]


# ============================================================
# Timezone
# ============================================================

def get_tz(config: ModuleType) -> timezone:
    """Return configured timezone object (default UTC+09)."""
    tz_str = os.environ.get("TIMEZONE") or config.TIMEZONE
    sign = 1 if "+" in tz_str else -1
    parts = tz_str.lstrip("+-").split(":")
    hours = int(parts[0])
    minutes = int(parts[1]) if len(parts) > 1 else 0
    return timezone(sign * timedelta(hours=hours, minutes=minutes))


def now_tz(config: ModuleType) -> datetime:
    """Return current time with configured timezone."""
    return datetime.now(get_tz(config))


# ============================================================
# Datetime validation
# ============================================================

def validate_datetime(dt_str: str, config: ModuleType) -> str:
    """Validate and normalize a datetime string, appending timezone.

    Accepted formats:
        YYYY-MM-DD           -> auto-complete to 00:00:00+TZ
        YYYY-MM-DD HH:MM:SS -> append +TZ

    Returns format: YYYY-MM-DD HH:MM:SS+09:00
    """
    tz_str = os.environ.get("TIMEZONE") or config.TIMEZONE
    # If already has timezone suffix, validate directly
    if "+" in dt_str[10:] or (len(dt_str) > 10 and "-" in dt_str[11:]):
        try:
            datetime.strptime(dt_str[:19], "%Y-%m-%d %H:%M:%S")
            return dt_str
        except ValueError:
            pass

    # YYYY-MM-DD HH:MM:SS
    try:
        datetime.strptime(dt_str, "%Y-%m-%d %H:%M:%S")
        return f"{dt_str}{tz_str}"
    except ValueError:
        pass

    # YYYY-MM-DD
    try:
        datetime.strptime(dt_str, "%Y-%m-%d")
        return f"{dt_str} 00:00:00{tz_str}"
    except ValueError:
        error(f"Invalid datetime format: {dt_str}, expected YYYY-MM-DD or YYYY-MM-DD HH:MM:SS")
        sys.exit(1)


# ============================================================
# Tool resolution
# ============================================================

def resolve_tool(tool_name: str, project_root: Path) -> str:
    """Resolve database tool executable path.

    Search order:
    1. Uppercase environment variable (MYSQL / PSQL)
    2. tools/ directory under project root
    3. Fallback to PATH lookup
    """
    for key in [tool_name.upper().replace("-GO", "").replace("-", ""),
                tool_name.upper().replace("-", "_")]:
        val = os.environ.get(key)
        if val:
            p = Path(val)
            if not p.is_absolute():
                p = project_root / p
            return str(p)

    suffix = ".exe" if sys.platform == "win32" else ""
    local = project_root / "tools" / f"{tool_name}{suffix}"
    if local.exists():
        return str(local)

    return tool_name  # fallback to PATH


# ============================================================
# SQL building
# ============================================================

def get_sql(start_date: str, config: ModuleType, script_dir: Path) -> str:
    """Build SQL query, replacing :start placeholder.

    Priority: env SQL > config.SQL > env SQL_FILE > config.SQL_FILE
    """
    sql = os.environ.get("SQL") or config.SQL

    if not sql:
        sql_file = os.environ.get("SQL_FILE") or config.SQL_FILE
        if sql_file:
            sql_path = Path(sql_file)
            if not sql_path.is_absolute():
                sql_path = script_dir / sql_path
            sql = sql_path.read_text(encoding="utf-8").strip()

    if not sql:
        error("SQL or SQL_FILE not configured")
        sys.exit(1)

    # Replace date placeholder (date has been validated, safe to replace)
    sql = sql.replace(":start", f"'{start_date}'")
    return sql


# ============================================================
# Database query
# ============================================================

def execute_query(sql: str, config: ModuleType, project_root: Path) -> str:
    """Execute SQL via configured database tool, return output."""
    tool_name = os.environ.get("DB_TOOL") or config.DB_TOOL
    tool = resolve_tool(tool_name, project_root)

    cmd = [tool, "-t", "-c", sql]

    log(f"Executing query: {tool_name} -t -c \"...\"")
    log(f"SQL: {sql}")
    result = subprocess.run(cmd, capture_output=True, text=True)

    if result.returncode != 0:
        error(f"{tool_name} stderr: {result.stderr.strip()}")
        raise RuntimeError(f"{tool_name} failed, rc={result.returncode}")

    return result.stdout.strip()


# ============================================================
# S3 upload
# ============================================================

def build_s3_path(template: str, now: datetime) -> str:
    """Replace date placeholders in S3 path template."""
    return (template
            .replace("<YYYY>", now.strftime("%Y"))
            .replace("<MM>", now.strftime("%m"))
            .replace("<DD>", now.strftime("%d")))


def is_truthy(value: object) -> bool:
    """Interpret config/env boolean-like values."""
    if isinstance(value, bool):
        return value
    if value is None:
        return False
    return str(value).strip().lower() in {"1", "true", "yes", "on"}


def get_store_in_ymd(config: ModuleType) -> bool:
    """Return whether result files should be stored under YYYYMMDD directory."""
    env_value = os.environ.get("STORE_IN_YMD")
    if env_value is not None:
        return is_truthy(env_value)
    return is_truthy(getattr(config, "STORE_IN_YMD", False))


def get_base_output_dir(config: ModuleType) -> Path:
    """Return base output directory before any YYYYMMDD suffix is applied."""
    return Path(os.environ.get("OUTPUT_DIR") or config.OUTPUT_DIR)


def get_output_dir(config: ModuleType, current_time: Optional[datetime] = None) -> Path:
    """Return effective output directory, optionally including YYYYMMDD subdirectory."""
    base_output_dir = get_base_output_dir(config)
    if not get_store_in_ymd(config):
        return base_output_dir

    if current_time is None:
        current_time = now_tz(config)
    return base_output_dir / current_time.strftime("%Y%m%d")


def upload_to_s3(local_path: str, s3_path: str, profile: str) -> None:
    """Upload file to S3 via AWS CLI."""
    cmd = ["aws", "s3", "cp", local_path, s3_path]
    if profile:
        cmd.extend(["--profile", profile])

    log(f"Uploading to {s3_path}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        error(f"aws s3 cp failed: {result.stderr.strip()}")
        raise RuntimeError(f"aws s3 cp failed, rc={result.returncode}")
    log("Upload complete")


# ============================================================
# Timestamp parsers
# ============================================================

def parse_pg_timestamptz(raw_value: str, config: ModuleType) -> datetime:
    """Parse PostgreSQL timestamptz output.

    Handles formats like:
        2026-04-15 16:34:19.334 +0900
        2026-04-15T08:10:15.863Z
        2026-04-15 16:34:19+0900
        2026-04-15T16:34:19+09:00
    """
    dt_part = raw_value.strip().rstrip("Z")
    dot_idx = dt_part.find(".")
    if dot_idx != -1:
        rest = dt_part[dot_idx + 1:]
        for i, ch in enumerate(rest):
            if ch in (" ", "+", "-"):
                dt_part = dt_part[:dot_idx] + rest[i:]
                break
        else:
            dt_part = dt_part[:dot_idx]

    dt_part = dt_part.strip()

    exec_dt = None
    for fmt in ("%Y-%m-%dT%H:%M:%S %z",
                "%Y-%m-%dT%H:%M:%S%z",
                "%Y-%m-%dT%H:%M:%S",
                "%Y-%m-%d %H:%M:%S %z",
                "%Y-%m-%d %H:%M:%S%z",
                "%Y-%m-%d %H:%M:%S"):
        try:
            exec_dt = datetime.strptime(dt_part, fmt)
            break
        except ValueError:
            continue

    if exec_dt is None:
        raise ValueError(f"Cannot parse timestamp: {raw_value}")

    if exec_dt.tzinfo is None:
        if raw_value.strip().endswith("Z"):
            exec_dt = exec_dt.replace(tzinfo=timezone.utc)
        else:
            exec_dt = exec_dt.replace(tzinfo=get_tz(config))

    return exec_dt


def parse_utc_timestamp(raw_value: str, config: ModuleType) -> datetime:
    """Parse MySQL UTC timestamp output.

    Handles formats like:
        2026-04-15T07:48:53Z
        2026-04-15 07:48:53
    Always interpreted as UTC.
    """
    ud = raw_value.strip().rstrip("Z")
    for fmt in ("%Y-%m-%dT%H:%M:%S",
                "%Y-%m-%d %H:%M:%S"):
        try:
            dt = datetime.strptime(ud, fmt)
            return dt.replace(tzinfo=timezone.utc)
        except ValueError:
            continue
    raise ValueError(f"Cannot parse timestamp: {raw_value}")


TIMESTAMP_PARSERS = {
    "pg_timestamptz": parse_pg_timestamptz,
    "utc": parse_utc_timestamp,
}


def get_timestamp_parser(name: str) -> Callable[[str, ModuleType], datetime]:
    """Return timestamp parser by configured name."""
    parser = TIMESTAMP_PARSERS.get(name)
    if parser is None:
        raise ValueError(f"unsupported timestamp parser: {name}")
    return parser


# ============================================================
# Generic check logic
# ============================================================

def check(
    start_date: str,
    upload: bool,
    config: ModuleType,
    project_root: Path,
    script_dir: Path,
    parse_timestamp: Optional[Callable[[str, ModuleType], datetime]] = None,
) -> str:
    """Execute the check and return the result line.

    Args:
        start_date: Start datetime (YYYY-MM-DD or YYYY-MM-DD HH:MM:SS), timezone from config.
        upload: Whether to upload result to S3.
        config: The config module with SQL, RESULT_PREFIX, etc.
        project_root: Project root directory.
        script_dir: Directory containing the checker script (for SQL file resolution).
        parse_timestamp: Function to parse the timestamp column from query output.
                         Defaults to parse_pg_timestamptz.

    Returns:
        Result line, format: [PREFIX] OK/UNKNOWN after <START> at <EXECUTE_TIME>
    """
    if parse_timestamp is None:
        parse_timestamp = parse_pg_timestamptz

    start_date = validate_datetime(start_date, config)

    sql = get_sql(start_date, config, script_dir)
    output = execute_query(sql, config, project_root)

    try:
        DATE_COL = 1

        if not output:
            log("Query returned empty result")
            count = False
        else:
            first_line = output.split("\n")[0]
            cols = [c.strip() for c in first_line.split("|")]
            date_str = cols[DATE_COL]
            log(f"date column value: {date_str}")

            exec_dt = parse_timestamp(date_str, config)

            # Parse start_date (e.g. "2026-04-15 00:00:00+09:00")
            start_naive = datetime.strptime(start_date[:19], "%Y-%m-%d %H:%M:%S")
            start_dt = start_naive.replace(tzinfo=get_tz(config))

            count = exec_dt >= start_dt
            log(f"date={exec_dt.isoformat()} >= start={start_dt.isoformat()} -> {count}")
    except (ValueError, IndexError) as e:
        error(f"Failed to parse query output: {e}, output: {output}")
        count = False

    now = now_tz(config)
    prefix = os.environ.get("RESULT_PREFIX") or config.RESULT_PREFIX
    status = "OK" if count else "UNKNOWN"
    execute_time = now.strftime("%Y-%m-%d %H:%M:%S%z")
    result_line = f"[{prefix}] {status} after {start_date} at {execute_time}"

    log(result_line)

    # Write result file
    output_dir = get_output_dir(config, now)
    output_dir.mkdir(parents=True, exist_ok=True)
    result_file = output_dir / f"{prefix.lower()}_check_{now.strftime('%Y%m%d_%H%M%S')}.txt"
    result_file.write_text(result_line + "\n", encoding="utf-8")
    log(f"Result written to {result_file}")

    # Optional S3 upload
    if upload:
        s3path = os.environ.get("S3PATH") or config.S3PATH
        if s3path:
            awsprofile = os.environ.get("AWSPROFILE") or config.AWSPROFILE
            s3_dest = build_s3_path(s3path, now)
            if not s3_dest.endswith("/"):
                s3_dest += "/"
            s3_dest += result_file.name
            upload_to_s3(str(result_file), s3_dest, awsprofile)

    return result_line

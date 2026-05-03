#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
notify_wrapper.py - Notification wrapper for checker scripts.

Designed as the --notify-exec target of any checker script (e.g.
runps_app_checker.py, eureka_checker.py).
Sends report to a normal channel, and additionally to an alert channel
when problems are detected.

Environment variables set by the calling checker:
    REPORT_TITLE        e.g. "[APP-CHECK] PROBLEM FOUND" or "[Eureka Check] ALL OK"
    REPORT_MESSAGE      Friendly summary text
    REPORT_FILE_TXT     Path to TXT report  (optional)
    REPORT_FILE_JSON    Path to JSON report  (optional)
    REPORT_FILE_HTM     Path to HTML report  (optional)
    REPORT_FILE_CARD    Path to Adaptive Card JSON file (used in card-json mode)
    REPORT_CARD_JSON    Adaptive Card JSON string       (fallback for card-json mode)

All settings are configured via built-in defaults or .env file.
CLI is kept minimal: only --env and --dry-run.

    NOTIFY_HOST              Messaging service host:port
    NOTIFY_PASSWORD          ePassword token
    NOTIFY_MODE              Send mode: text | card | card-json
    NOTIFY_PROFILE           Normal channel profile
    NOTIFY_CHANNEL           Normal channel name
    NOTIFY_ALERT_PROFILE     Alert channel profile  (sends on problem)
    NOTIFY_ALERT_CHANNEL     Alert channel name      (sends on problem)
    NOTIFY_PROBLEM_KEYWORDS  Comma-separated keywords to detect problems

Usage:
    # As notify-exec (simplest form):
    python runps_app_checker.py  --notify-exec "python src/python/notify_wrapper.py" ...
    python eureka_checker.py     --notify-exec "python src/python/notify_wrapper.py" ...

    # With custom .env:
    python src/python/notify_wrapper.py --env my_channels.env

    # Dry run:
    python src/python/notify_wrapper.py --dry-run
"""

import argparse
import io
import os
import subprocess
import sys
from pathlib import Path

# Force UTF-8 output regardless of system locale (e.g. cp932)
if sys.stdout.encoding != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
if sys.stderr.encoding != "utf-8":
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = os.environ.get("PROJECT_ROOT", SCRIPT_DIR.parents[1])
DEV_TOOLS_DIR = f"{PROJECT_ROOT}/tools"


DEFAULT_HOST = "10.2.3.23:3001"
# Note: the default password is just a placeholder and should be overridden by environment variables or .env files in real usage.
DEFAULT_PASSWORD = "53Ap28oiKpWxPv/xLFHKUQ==@Lq8K1incOvs4Qt6zZM8X8A=="
DEFAULT_PROFILE = "victor-test2"
DEFAULT_CHANNEL = "Default"
DEFAULT_ALERT_PROFILE = "victor-test2"
DEFAULT_ALERT_CHANNEL = "Default"
DEFAULT_MODE = "card-json"
DEFAULT_PROBLEM_KEYWORDS = "PROBLEM"


# ── .env loader ──────────────────────────────────────────────
from sys_common import load_env_file  # Reuse the same .env loader from sys_common.py
# def load_env_file(path: Path) -> None:
#     if not path.is_file():
#         return
#     with open(path, encoding="utf-8") as f:
#         for line in f:
#             line = line.strip()
#             if not line or line.startswith("#"):
#                 continue
#             if line.startswith("export "):
#                 line = line[len("export "):].strip()
#             if "=" not in line:
#                 continue
#             key, _, value = line.partition("=")
#             key = key.strip()
#             if not key:
#                 continue
#             value = value.strip()
#             if value.startswith("'") and value.endswith("'"):
#                 value = value[1:-1]
#             else:
#                 if value.startswith('"') and value.endswith('"'):
#                     value = value[1:-1]
#                 value = os.path.expandvars(os.path.expanduser(value))
#             os.environ[key] = value


# ── helpers ──────────────────────────────────────────────────

def has_problem(title: str, keywords: str) -> bool:
    """Check if title contains any problem keyword."""
    kw_list = [k.strip() for k in keywords.split(",") if k.strip()]
    return any(kw in title for kw in kw_list)


def call_send_message(
    mode: str,
    host: str,
    profile: str,
    channel: str,
    password: str,
    content: str,
    card_file: str,
    label: str,
    dry_run: bool,
    extra_args: list = None,
) -> int:
    """Invoke send_message.py to send a message."""
    TOOL_DIR = os.environ.get("TOOL_DIR", DEV_TOOLS_DIR)
    send_script = Path(TOOL_DIR) / "send_message.py"

    cmd = [
        sys.executable, str(send_script),
        "--mode", mode,
        "--host", host,
        "--profile", profile,
        "--channel", channel,
        "--password", password,
    ]

    # Content source: prefer card file for card-json, inline for text/card
    if mode == "card-json" and card_file:
        cmd += ["-f", card_file]
    elif content:
        cmd += ["--content", content]
    elif card_file:
        cmd += ["-f", card_file]
    else:
        print(f"[{label}] skip: no content to send", file=sys.stderr)
        return 0

    if extra_args:
        cmd += extra_args

    if dry_run:
        print(f"[{label}] DRY RUN: {' '.join(cmd)}")
        return 0

    print(f"[{label}] sending to {profile}/{channel} (mode={mode}) ...")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.stdout:
        print(f"[{label}] {result.stdout.strip()}")
    if result.stderr:
        print(f"[{label}] {result.stderr.strip()}")
    if result.returncode != 0:
        print(f"[{label}] send failed, rc={result.returncode}", file=sys.stderr)
    else:
        print(f"[{label}] send ok")
    return result.returncode


# ── CLI ──────────────────────────────────────────────────────

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Notification wrapper: send report to normal + alert channels.",
    )
    parser.add_argument("--env", dest="env_file",
                        help="Additional .env file to load")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print commands without sending")
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    # Load .env files
    load_env_file(SCRIPT_DIR / ".env")
    load_env_file(SCRIPT_DIR / "notify_wrapper.env")
    if args.env_file:
        p = Path(args.env_file)
        if not p.is_file():
            print(f"Error: env file not found: {args.env_file}", file=sys.stderr)
            sys.exit(1)
        load_env_file(p)

    # Resolve parameters: env > built-in default
    host = os.environ.get("NOTIFY_HOST", DEFAULT_HOST)
    password = os.environ.get("NOTIFY_PASSWORD", DEFAULT_PASSWORD)
    mode = os.environ.get("NOTIFY_MODE", DEFAULT_MODE)
    profile = os.environ.get("NOTIFY_PROFILE", DEFAULT_PROFILE)
    channel = os.environ.get("NOTIFY_CHANNEL", DEFAULT_CHANNEL)
    alert_profile = os.environ.get("NOTIFY_ALERT_PROFILE", DEFAULT_ALERT_PROFILE)
    alert_channel = os.environ.get("NOTIFY_ALERT_CHANNEL", DEFAULT_ALERT_CHANNEL)
    problem_keywords = os.environ.get("NOTIFY_PROBLEM_KEYWORDS", DEFAULT_PROBLEM_KEYWORDS)

    # Read report data from environment (set by runs_app_checker.py)
    title = os.environ.get("REPORT_TITLE", "")
    message = os.environ.get("REPORT_MESSAGE", "")
    card_file = os.environ.get("REPORT_FILE_CARD", "")
    card_json = os.environ.get("REPORT_CARD_JSON", "")

    if not title:
        print("Warning: REPORT_TITLE not set", file=sys.stderr)

    # Determine content: for card-json use card file, for text/card use message
    if mode == "card-json":
        content = card_json
    else:
        content = message or title

    detected_problem = has_problem(title, problem_keywords)

    print(f"Title: {title}")
    print(f"Problem detected: {detected_problem}")
    print(f"Mode: {mode}")

    rc = 0

    # ── 1. Always send to normal channel ──
    r = call_send_message(
        mode=mode, host=host, profile=profile, channel=channel,
        password=password, content=content, card_file=card_file,
        label="normal", dry_run=args.dry_run,
    )
    if r != 0:
        rc = r

    # ── 2. On problem, also send to alert channel ──
    if detected_problem and alert_profile and alert_channel:
        r = call_send_message(
            mode=mode, host=host, profile=alert_profile, channel=alert_channel,
            password=password, content=content, card_file=card_file,
            label="alert", dry_run=args.dry_run,
        )
        if r != 0:
            rc = r
    elif detected_problem:
        print("[alert] alert-profile / alert-channel not configured, skip alert")

    sys.exit(rc)


if __name__ == "__main__":
    main()

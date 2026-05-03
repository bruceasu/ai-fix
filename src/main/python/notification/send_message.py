#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
send_message.py - Send messages to Teams via messaging service.

Supports three modes (matching the three service APIs):
    text       /message/sendMessage/...              plain text
    card       /message/sendAdaptiveCard/...         text with styling
    card-json  /message/sendAdaptiveCardJson/...     full Adaptive Card JSON

Usage:
    # Adaptive Card JSON from file
    python send_message.py --mode card-json -f card.json

    # Adaptive Card JSON from stdin
    echo '{"type":"AdaptiveCard",...}' | python send_message.py -f -

    # Simple text message
    python send_message.py --mode text --content "Hello world"

    # Styled card with subject
    python send_message.py --mode card --content "Body text" --subject "Title"

    # Load settings from .env file
    python send_message.py --env send_message.env -f card.json

Environment variables (also loadable from .env):
    SEND_MESSAGE_URL       Full service URL (overrides --host/--profile/--channel)
    SEND_MESSAGE_HOST      Service host:port (default: 10.2.3.23:3001)
    SEND_MESSAGE_PROFILE   Profile name (default: default)
    SEND_MESSAGE_CHANNEL   Channel name (default: Default)
    SEND_MESSAGE_PASSWORD  ePassword token
    SEND_MESSAGE_MODE      Mode: text | card | card-json
"""

import argparse
import json
import os
import ssl
import sys
import urllib.request
import urllib.error
from pathlib import Path

DEFAULT_HOST = "10.2.3.23:3001"
DEFAULT_PROFILE = "default"
DEFAULT_CHANNEL = "Default"
DEFAULT_PASSWORD = "53Ap28oiKpWxPv/xLFHKUQ==@Lq8K1incOvs4Qt6zZM8X8A=="

# Adaptive Card style values:
#   containerStyle: Default, Accent, Emphasis, Good, Attention, Warning
#   fontColor:      Default, Dark, Light, Accent, Good, Attention, Warning

API_PATHS = {
    "text":      "sendMessage",
    "card":      "sendAdaptiveCard",
    "card-json": "sendAdaptiveCardJson",
}


# ── .env loader ──────────────────────────────────────────────

def load_env_file(path: Path) -> None:
    """Load KEY=VALUE pairs from env file into os.environ."""
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
            if value.startswith("'") and value.endswith("'"):
                value = value[1:-1]
            else:
                if value.startswith('"') and value.endswith('"'):
                    value = value[1:-1]
                value = os.path.expandvars(os.path.expanduser(value))
            os.environ[key] = value


# ── SSL ──────────────────────────────────────────────────────

def create_ssl_context() -> ssl.SSLContext:
    """Create an SSL context that skips certificate verification (self-signed)."""
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    return ctx


# ── URL builder ──────────────────────────────────────────────

def build_url(mode: str, host: str, profile: str, channel: str) -> str:
    api = API_PATHS[mode]
    return f"https://{host}/message/{api}/{profile}/{channel}"


# ── HTTP post ────────────────────────────────────────────────

def post_json(url: str, body: dict, timeout: int = 30) -> str:
    body_bytes = json.dumps(body, ensure_ascii=False).encode("utf-8")

    req = urllib.request.Request(url, data=body_bytes, method="POST")
    req.add_header("Content-Type", "application/json")

    ctx = create_ssl_context()

    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            charset = resp.headers.get_content_charset() or "utf-8"
            return resp.read().decode(charset)
    except urllib.error.HTTPError as e:
        error_body = ""
        try:
            error_body = e.read().decode("utf-8", errors="ignore")
        except Exception:
            pass
        print(f"HTTP error: code={e.code}, reason={e.reason}", file=sys.stderr)
        if error_body:
            print(f"Response body: {error_body}", file=sys.stderr)
        sys.exit(1)
    except urllib.error.URLError as e:
        print(f"Request failed: {e.reason}", file=sys.stderr)
        sys.exit(1)


# ── Senders per mode ─────────────────────────────────────────

def send_text(url: str, content: str, password: str, sender: str, timeout: int) -> str:
    """sendMessage API - plain text."""
    body = {"from": sender, "content": content, "ePassword": password}
    return post_json(url, body, timeout)


def send_card(
    url: str, content: str, password: str, sender: str, timeout: int,
    subject: str = "",
    subject_style: str = "Default",
    subject_color: str = "Default",
    message_style: str = "Good",
    message_color: str = "Default",
) -> str:
    """sendAdaptiveCard API - text with styling options."""
    body = {
        "from": sender,
        "content": content,
        "ePassword": password,
        "subjectContent": subject,
        "subjectContainerStyle": subject_style,
        "subjectFontColor": subject_color,
        "messageContainerStyle": message_style,
        "messageFontColor": message_color,
    }
    return post_json(url, body, timeout)


def send_card_json(url: str, content: str, password: str, sender: str, timeout: int) -> str:
    """sendAdaptiveCardJson API - full Adaptive Card JSON."""
    body = {"from": sender, "content": content, "ePassword": password}
    return post_json(url, body, timeout)


# ── CLI ──────────────────────────────────────────────────────

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Send messages to Teams via messaging service (text / card / card-json).",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""\
examples:
  %(prog)s -f card.json
  %(prog)s --mode text --content "Hello"
  %(prog)s --mode card --content "Body" --subject "Title"
  %(prog)s --env send_message.env -f card.json
  echo '...' | %(prog)s -f -
""",
    )

    # .env
    parser.add_argument(
        "--env", dest="env_file",
        help="Path to .env file to load before processing (KEY=VALUE pairs)",
    )

    # mode
    parser.add_argument(
        "--mode", choices=["text", "card", "card-json"],
        help="API mode (default: text, auto-detected from --url if set)",
    )

    # content source
    parser.add_argument(
        "-f", "--file",
        help="File with content. Use '-' for stdin.",
    )
    parser.add_argument(
        "--content",
        help="Content string (alternative to -f).",
    )

    # connection
    parser.add_argument("--url", help="Full service URL (overrides --host/--profile/--channel)")
    parser.add_argument("--host", help=f"Service host:port (default: {DEFAULT_HOST})")
    parser.add_argument("--profile", help=f"Profile name (default: {DEFAULT_PROFILE})")
    parser.add_argument("--channel", help=f"Channel name (default: {DEFAULT_CHANNEL})")
    parser.add_argument("--password", help="ePassword token")
    parser.add_argument("--from-sender", dest="sender", default="", help="'from' field (default: empty)")
    parser.add_argument("--timeout", type=int, default=30, help="HTTP timeout in seconds (default: 30)")

    # card mode styling
    card_group = parser.add_argument_group("card mode options (--mode card)")
    card_group.add_argument("--subject", default="", help="Subject line text")
    card_group.add_argument("--subject-style", default="Default",
                            help="Subject container style (default: Default)")
    card_group.add_argument("--subject-color", default="Default",
                            help="Subject font color (default: Default)")
    card_group.add_argument("--message-style", default="Good",
                            help="Message container style (default: Good)")
    card_group.add_argument("--message-color", default="Default",
                            help="Message font color (default: Default)")

    return parser.parse_args()


def detect_mode_from_url(url: str) -> str:
    """Auto-detect mode from URL path."""
    if "/sendAdaptiveCardJson/" in url:
        return "card-json"
    if "/sendAdaptiveCard/" in url:
        return "card"
    if "/sendMessage/" in url:
        return "text"
    return "text"  # default if not detectable


def main() -> None:
    args = parse_args()

    # Load .env file(s)
    script_dir = Path(__file__).resolve().parent
    load_env_file(script_dir / ".env")
    load_env_file(script_dir / "send_message.env")
    if args.env_file:
        p = Path(args.env_file)
        if not p.is_file():
            print(f"Error: env file not found: {args.env_file}", file=sys.stderr)
            sys.exit(1)
        load_env_file(p)

    # Resolve parameters: CLI > env > default
    password = args.password or os.environ.get("SEND_MESSAGE_PASSWORD", DEFAULT_PASSWORD)
    host = args.host or os.environ.get("SEND_MESSAGE_HOST", DEFAULT_HOST)
    profile = args.profile or os.environ.get("SEND_MESSAGE_PROFILE", DEFAULT_PROFILE)
    channel = args.channel or os.environ.get("SEND_MESSAGE_CHANNEL", DEFAULT_CHANNEL)

    # Resolve mode
    explicit_url = args.url or os.environ.get("SEND_MESSAGE_URL", "")
    if args.mode:
        mode = args.mode
    elif explicit_url:
        mode = detect_mode_from_url(explicit_url)
    else:
        mode = os.environ.get("SEND_MESSAGE_MODE", "text")

    # Resolve URL
    if explicit_url:
        url = explicit_url
    else:
        url = build_url(mode, host, profile, channel)

    # Resolve content
    content = None
    if args.file:
        if args.file == "-":
            content = sys.stdin.read()
        else:
            with open(args.file, "r", encoding="utf-8") as f:
                content = f.read()
    elif args.content:
        content = args.content
    else:
        print("Error: must provide -f/--file or --content", file=sys.stderr)
        sys.exit(1)

    content = content.strip()
    if not content:
        print("Error: content is empty", file=sys.stderr)
        sys.exit(1)

    # Validate JSON for card-json mode
    if mode == "card-json":
        try:
            json.loads(content)
        except json.JSONDecodeError as e:
            print(f"Error: content is not valid JSON (required for card-json mode): {e}", file=sys.stderr)
            sys.exit(1)

    # Send
    print(f"Mode: {mode}, URL: {url}", file=sys.stderr)

    if mode == "text":
        result = send_text(url, content, password, args.sender, args.timeout)
    elif mode == "card":
        result = send_card(
            url, content, password, args.sender, args.timeout,
            subject=args.subject,
            subject_style=args.subject_style,
            subject_color=args.subject_color,
            message_style=args.message_style,
            message_color=args.message_color,
        )
    else:
        result = send_text(url, content, password, args.sender, args.timeout)

    print(result)


if __name__ == "__main__":
    main()

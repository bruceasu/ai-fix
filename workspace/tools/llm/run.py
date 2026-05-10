import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path


def add_runtime_path() -> None:
    scripts_dir = os.getenv("AI_FIX_PYTHON_SCRIPTS_DIR")
    if scripts_dir:
        runtime_dir = Path(scripts_dir).resolve() / "_runtime"
    else:
        runtime_dir = Path(__file__).resolve().parents[3] / "src" / "main" / "python" / "_runtime"
    sys.path.insert(0, str(runtime_dir))


add_runtime_path()

from tool_runtime import emit_result, failure, load_json, normalize_optional, required_keys_missing, strip_fence, success  # noqa: E402


def call_openai_compatible(base_url: str, api_key: str, model: str, prompt: str, system_prompt: str) -> str:
    url = base_url.rstrip("/") + "/chat/completions"
    body = {
        "model": model,
        "messages": [],
        "temperature": 0,
    }
    if system_prompt.strip():
        body["messages"].append({"role": "system", "content": system_prompt})
    body["messages"].append({"role": "user", "content": prompt})
    request = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        payload = json.loads(response.read().decode("utf-8", errors="replace"))
    choices = payload.get("choices") or []
    if not choices:
        return ""
    message = choices[0].get("message") or {}
    return str(message.get("content") or "")


def call_ollama(base_url: str, model: str, prompt: str, system_prompt: str) -> str:
    effective_prompt = prompt if not system_prompt.strip() else f"System instruction:\n{system_prompt}\n\nUser request:\n{prompt}"
    body = {
        "model": model,
        "prompt": effective_prompt,
        "stream": False,
    }
    request = urllib.request.Request(
        base_url.rstrip("/") + "/api/generate",
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        payload = json.loads(response.read().decode("utf-8", errors="replace"))
    return str(payload.get("response") or "")


def parse_structured(output: str, output_format: str, required_keys: str) -> dict[str, object]:
    cleaned = strip_fence(output)
    if output_format == "json":
        structured = load_json(cleaned)
    elif output_format == "yaml":
        try:
            import yaml  # type: ignore

            structured_obj = yaml.safe_load(cleaned)
            if not isinstance(structured_obj, dict):
                raise ValueError("Expected YAML object")
            structured = structured_obj
        except ModuleNotFoundError:
            structured = load_json(cleaned)
    else:
        raise ValueError(f"Unsupported format: {output_format}. Use text, yaml, or json.")

    missing = required_keys_missing(required_keys, structured)
    if missing:
        raise ValueError("Missing required keys: " + ", ".join(missing))
    return structured


def validate_against_schema(structured: dict, schema: dict) -> list[str]:
    """Validate `structured` against `schema` and return a list of error messages.

    Uses `jsonschema` if available, otherwise performs a minimal fallback validation
    (checks `required` and top-level `type` for declared properties).
    """
    errors: list[str] = []
    try:
        import jsonschema  # type: ignore

        validator = jsonschema.Draft7Validator(schema)
        for err in sorted(validator.iter_errors(structured), key=lambda e: e.path):
            path = ".".join([str(p) for p in err.path]) or "<root>"
            errors.append(f"{path}: {err.message}")
        return errors
    except ModuleNotFoundError:
        # Minimal fallback: check `required` and top-level property types
        req = schema.get("required") or []
        for key in req:
            if key not in structured:
                errors.append(f"required property missing: {key}")

        props = schema.get("properties") or {}
        type_map = {
            "string": str,
            "number": (int, float),
            "integer": int,
            "boolean": bool,
            "object": dict,
            "array": list,
        }
        for name, spec in props.items():
            if name not in structured:
                continue
            expected = spec.get("type")
            if expected:
                pytype = type_map.get(expected)
                if pytype and not isinstance(structured[name], pytype):
                    errors.append(f"{name}: expected {expected}")
        return errors
    except Exception as exc:
        return [f"schema validator error: {exc}"]


def main() -> None:
    epilog = (
        "Schema helpers: if --schema-file is not provided, the tool will look for "
        "workspace/tools/llm/schema/tool_response.schema.json relative to this script.\n"
        "Example:\n"
        "  python workspace/tools/llm/run.py --prompt '返回符合 schema 的 JSON' --format json \\" \
        "--schema-file workspace/tools/llm/schema/tool_response.schema.json --require-schema"
    )
    parser = argparse.ArgumentParser(
        description="Call the configured AI provider explicitly from a workflow.", epilog=epilog, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--prompt", required=True)
    parser.add_argument("--system-prompt", default="")
    parser.add_argument("--provider", default="")
    parser.add_argument("--model", default="")
    parser.add_argument("--format", default="text")
    parser.add_argument("--required-keys", default="")
    parser.add_argument(
        "--schema-file",
        default="",
        help=(
            "Path to JSON Schema file to validate structured output. If omitted, "
            "the tool will auto-discover workspace/tools/llm/schema/tool_response.schema.json"
        ),
    )
    parser.add_argument("--schema", default="", help="Inline JSON Schema string to validate structured output")
    parser.add_argument("--require-schema", action="store_true", help="Fail the run if schema validation fails")
    args = parser.parse_args()

    prompt = normalize_optional(args.prompt) or ""
    if not prompt:
        emit_result(failure("llm", "Missing required argument: prompt"))
        raise SystemExit(1)

    provider = (normalize_optional(args.provider) or os.getenv("AI_FIX_PROVIDER") or os.getenv("OPENAI_PROVIDER") or "openai").lower()
    model = normalize_optional(args.model) or os.getenv("AI_FIX_MODEL") or os.getenv("OPENAI_MODEL") or "gpt-4o-mini"
    system_prompt = normalize_optional(args.system_prompt) or ""
    output_format = (normalize_optional(args.format) or "text").lower()
    required_keys = normalize_optional(args.required_keys) or ""
    schema_file = normalize_optional(args.schema_file) or ""
    schema_inline = normalize_optional(args.schema) or ""
    require_schema = bool(args.require_schema)

    # Auto-discover default schema if none provided
    if not schema_file and not schema_inline:
        default_schema = Path(__file__).resolve().parent / "schema" / "tool_response.schema.json"
        if default_schema.exists():
            schema_file = str(default_schema)

    try:
        if provider == "ollama":
            base_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
            output = call_ollama(base_url, model, prompt, system_prompt)
        else:
            if provider == "groq":
                api_key = os.getenv("GROQ_API_KEY", "")
                base_url = os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1")
            else:
                api_key = os.getenv("OPENAI_API_KEY", "")
                base_url = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
            if not api_key.strip():
                raise ValueError(f"Missing API key for provider: {provider}")
            output = call_openai_compatible(base_url, api_key, model, prompt, system_prompt)

        data = {
            "provider": provider,
            "model": model,
            "promptLength": len(prompt),
            "hasSystemPrompt": bool(system_prompt.strip()),
            "format": output_format,
        }
        if required_keys.strip():
            data["requiredKeys"] = [key.strip() for key in required_keys.split(",") if key.strip()]
        if output_format != "text":
            structured = parse_structured(output, output_format, required_keys)

            schema_obj = None
            if schema_inline:
                try:
                    schema_obj = json.loads(schema_inline)
                except Exception as exc:
                    raise ValueError(f"Invalid inline schema JSON: {exc}")
            elif schema_file:
                try:
                    with open(schema_file, "r", encoding="utf-8") as f:
                        schema_obj = json.load(f)
                except Exception as exc:
                    raise ValueError(f"Unable to load schema file: {exc}")

            data["structured"] = structured
            if schema_obj is not None:
                errors = validate_against_schema(structured, schema_obj)
                data["schemaUsed"] = True
                if errors:
                    data["schemaErrors"] = errors
                    if require_schema:
                        raise ValueError("Schema validation failed: " + "; ".join(errors))
                else:
                    data["schemaValid"] = True
        emit_result(success("llm", output, data))
    except Exception as exc:
        emit_result(failure("llm", str(exc)))
        raise SystemExit(1)


if __name__ == "__main__":
    main()

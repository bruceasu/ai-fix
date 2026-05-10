import argparse
import json
import os
import subprocess
import sys
import urllib.request
from pathlib import Path


def runtime_dir() -> Path:
    scripts_dir = os.getenv("AI_FIX_PYTHON_SCRIPTS_DIR")
    if scripts_dir:
        return Path(scripts_dir).resolve() / "_runtime"
    return Path(__file__).resolve().parent / "_runtime"


def add_runtime_path() -> None:
    sys.path.insert(0, str(runtime_dir()))


add_runtime_path()

from tool_runtime import emit_result, failure, load_json, normalize_optional, parse_bool, parse_int, success  # noqa: E402


def resolve_tools_dir(explicit_tools_dir: str | None = None) -> Path:
    if explicit_tools_dir and explicit_tools_dir.strip():
        return Path(explicit_tools_dir).expanduser().resolve()
    env_tools_dir = os.getenv("AI_FIX_TOOLS_DIR")
    if env_tools_dir and env_tools_dir.strip():
        return Path(env_tools_dir).expanduser().resolve()
    return Path(__file__).resolve().parents[3] / "workspace" / "tools"


def resolve_skills_dir() -> Path:
    env_skills_dir = os.getenv("AI_FIX_SKILLS_DIR")
    if env_skills_dir and env_skills_dir.strip():
        return Path(env_skills_dir).expanduser().resolve()
    return Path(__file__).resolve().parents[3] / "workspace" / "skills"


def load_tool_definition(tool_home: Path) -> dict[str, object]:
    yaml_path = None
    for candidate in ("tool.yaml", "tool.yml", "tool.json"):
        maybe = tool_home / candidate
        if maybe.is_file():
            yaml_path = maybe
            break
    if yaml_path is None:
        raise FileNotFoundError(f"Missing tool definition in {tool_home}")
    if yaml_path.suffix == ".json":
        return load_json(yaml_path.read_text(encoding="utf-8"))
    try:
        import yaml  # type: ignore

        loaded = yaml.safe_load(yaml_path.read_text(encoding="utf-8"))
        if not isinstance(loaded, dict):
            raise ValueError("tool definition must be a mapping")
        return loaded
    except ModuleNotFoundError as exc:
        raise RuntimeError(
            "PyYAML is required to load tool.yaml files for the Python dispatcher."
        ) from exc


def load_skill_definition(skill_home: Path) -> dict[str, object]:
    yaml_path = None
    for candidate in ("skill.yaml", "skill.yml", "skill.json"):
        maybe = skill_home / candidate
        if maybe.is_file():
            yaml_path = maybe
            break
    if yaml_path is None:
        raise FileNotFoundError(f"Missing skill definition in {skill_home}")
    if yaml_path.suffix == ".json":
        return load_json(yaml_path.read_text(encoding="utf-8"))
    try:
        import yaml  # type: ignore

        loaded = yaml.safe_load(yaml_path.read_text(encoding="utf-8"))
        if not isinstance(loaded, dict):
            raise ValueError("skill definition must be a mapping")
        return loaded
    except ModuleNotFoundError as exc:
        raise RuntimeError(
            "PyYAML is required to load skill.yaml files for the Python dispatcher."
        ) from exc


def normalize_args(args_obj: object) -> dict[str, object]:
    if isinstance(args_obj, dict):
        return dict(args_obj)
    return {}


def list_tool_definitions(tools_dir: Path) -> list[dict[str, object]]:
    if not tools_dir.is_dir():
        return []
    tools: list[dict[str, object]] = []
    for child in sorted(tools_dir.iterdir()):
        if not child.is_dir():
            continue
        definition = None
        for candidate in ("tool.yaml", "tool.yml", "tool.json"):
            maybe = child / candidate
            if maybe.is_file():
                definition = load_tool_definition(child)
                break
        if definition:
            tools.append(definition)
    return tools


def list_skill_definitions(skills_dir: Path) -> list[dict[str, object]]:
    if not skills_dir.is_dir():
        return []
    skills: list[dict[str, object]] = []
    for child in sorted(skills_dir.iterdir()):
        if not child.is_dir():
            continue
        definition = None
        for candidate in ("skill.yaml", "skill.yml", "skill.json"):
            maybe = child / candidate
            if maybe.is_file():
                definition = load_skill_definition(child)
                break
        if definition:
            skills.append(definition)
    return skills


def apply_defaults(definition: dict[str, object], args: dict[str, object]) -> dict[str, object]:
    result = dict(args)
    for arg_def in definition.get("arguments") or []:
        if not isinstance(arg_def, dict):
            continue
        name = str(arg_def.get("name") or "").strip()
        if not name or name in result:
            continue
        default_value = arg_def.get("defaultValue")
        if default_value is not None and str(default_value).strip() != "":
            result[name] = default_value
    return result


def missing_required_args(definition: dict[str, object], args: dict[str, object]) -> list[str]:
    missing: list[str] = []
    for arg_def in definition.get("arguments") or []:
        if not isinstance(arg_def, dict):
            continue
        if not arg_def.get("required"):
            continue
        name = str(arg_def.get("name") or "").strip()
        if not name:
            continue
        value = args.get(name)
        if value is None or str(value).strip() == "":
            missing.append(name)
    return missing


def build_fill_args_prompt(definition: dict[str, object], missing: list[str], existing_args: dict[str, object]) -> str:
    lines = [
        "You are an assistant that helps fill missing tool invocation parameters.",
        f"Tool: {definition.get('name') or ''}",
        f"Required missing parameters: {', '.join(missing)}",
        "",
        "Parameter details:",
    ]
    for arg_def in definition.get("arguments") or []:
        if not isinstance(arg_def, dict):
            continue
        name = str(arg_def.get("name") or "").strip()
        if not name or name not in missing:
            continue
        line = f"- {name}: "
        description = str(arg_def.get("description") or "").strip()
        if description:
            line += description + " "
        arg_type = str(arg_def.get("type") or "").strip()
        if arg_type:
            line += f"(type: {arg_type})"
        lines.append(line)
    lines.extend([
        "",
        "Existing args (do not change unless needed):",
        json.dumps(existing_args or {}, ensure_ascii=False),
        "",
        "Please provide a JSON object that supplies values for the missing parameters only.",
        "Return ONLY valid JSON (no surrounding text). Use best effort based on the parameter descriptions.",
    ])
    return "\n".join(lines)


def call_openai_compatible(base_url: str, api_key: str, model: str, prompt: str, system_prompt: str = "") -> str:
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


def call_ollama(base_url: str, model: str, prompt: str, system_prompt: str = "") -> str:
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


def generate_llm_text(provider: str, model: str, prompt: str) -> str:
    provider_name = (provider or "openai").strip().lower()
    if provider_name == "ollama":
        return call_ollama(os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"), model, prompt)
    if provider_name == "groq":
        api_key = os.getenv("GROQ_API_KEY", "")
        base_url = os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1")
    else:
        api_key = os.getenv("OPENAI_API_KEY", "")
        base_url = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
    if not api_key.strip():
        raise ValueError(f"Missing API key for provider: {provider_name}")
    return call_openai_compatible(base_url, api_key, model, prompt)


def try_fill_missing_args(definition: dict[str, object], missing: list[str], args: dict[str, object], provider: str, model: str) -> dict[str, object]:
    if not missing:
        return args
    if not provider or not model:
        return args
    prompt = build_fill_args_prompt(definition, missing, args)
    filled_raw = generate_llm_text(provider, model, prompt)
    filled = load_json(filled_raw)
    if isinstance(filled, dict) and filled:
        merged = dict(args)
        merged.update(filled)
        return merged
    return args


def render_token(token: str, args: dict[str, object], tool_home: Path, scripts_dir: Path) -> str:
    rendered = token.replace("${toolHome}", str(tool_home).replace("\\", "/"))
    rendered = rendered.replace("${pythonScriptsDir}", str(scripts_dir).replace("\\", "/"))
    rendered = rendered.replace("${pythonHome}", str(scripts_dir).replace("\\", "/"))
    for key, value in args.items():
        placeholder = "${" + key + "}"
        if placeholder in rendered:
            rendered = rendered.replace(placeholder, "" if value is None else str(value))
    return rendered


def build_dispatch_prompt(user_instruction: str, tools: list[dict[str, object]], skills: list[dict[str, object]]) -> str:
    lines = [
        "You are a strict dispatcher. Output ONLY valid JSON with this schema:",
        '{ "actionType": "tool" | "skill", "name": "capability-name", "args": { ... }, "confirm": true|false }',
        "Choose exactly one capability from the available tools or skills.",
        "Do not invent new names.",
        "",
        "Available tools:",
    ]
    for tool in tools:
        line = f"- {tool.get('name') or ''}"
        description = str(tool.get("description") or "").strip()
        if description:
            line += f": {description}"
        lines.append(line)
        for arg_def in tool.get("arguments") or []:
            if not isinstance(arg_def, dict):
                continue
            arg_name = str(arg_def.get("name") or "").strip()
            if not arg_name:
                continue
            lines.append(f"  - {arg_name}{'*' if arg_def.get('required') else ''}")
    lines.append("")
    lines.append("Available skills:")
    for skill in skills:
        line = f"- {skill.get('name') or ''}"
        description = str(skill.get("description") or "").strip()
        if description:
            line += f": {description}"
        lines.append(line)
    lines.extend([
        "",
        "User instruction:",
        user_instruction or "",
    ])
    return "\n".join(lines)


def parse_dispatch_intent(raw: str) -> dict[str, object]:
    intent = load_json(strip_fence(raw))
    if not isinstance(intent, dict):
        raise ValueError("Dispatcher LLM did not return a JSON object")
    action_type = str(intent.get("actionType") or intent.get("action") or "tool").strip().lower()
    name = str(intent.get("name") or intent.get("tool") or "").strip()
    if not name:
        raise ValueError("Dispatcher LLM did not return a name")
    args = intent.get("args")
    if not isinstance(args, dict):
        args = {}
    confirm = bool(intent.get("confirm") or intent.get("confirmed") or False)
    return {
        "actionType": action_type if action_type in {"tool", "skill"} else "tool",
        "name": name,
        "args": args,
        "confirm": confirm,
    }


def resolve_launcher(program: str) -> list[str]:
    if program.lower() in {"python", "python3", "py"}:
        return [sys.executable]
    return [program]


def dispatch_capability(user_instruction: str, tools_dir: Path, provider: str, model: str, require_confirm: bool) -> dict[str, object]:
    tools = list_tool_definitions(tools_dir)
    skills = list_skill_definitions(resolve_skills_dir())
    prompt = build_dispatch_prompt(user_instruction, tools, skills)
    raw = generate_llm_text(provider, model, prompt)
    intent = parse_dispatch_intent(raw)
    action_type = str(intent.get("actionType") or "tool").lower()
    name = str(intent.get("name") or "").strip()
    args = intent.get("args") if isinstance(intent.get("args"), dict) else {}
    confirm = bool(intent.get("confirm") or False)
    if require_confirm and action_type == "tool":
        confirm = True

    if action_type == "skill":
        return {
            "ok": True,
            "toolName": name,
            "output": "",
            "data": {
                "actionType": "skill",
                "name": name,
                "args": args,
                "confirm": confirm,
            },
            "error": "",
        }

    if action_type != "tool":
        raise ValueError(f"Unsupported action type: {action_type}")

    definition = next((tool for tool in tools if str(tool.get("name") or "") == name), None)
    if definition is None:
        raise ValueError(f"Unknown tool: {name}")
    tool_home = tools_dir / name
    return execute_external(name, tool_home, definition, json.dumps(args, ensure_ascii=False), confirm, provider, model)


def execute_external(tool_name: str, tool_home: Path, definition: dict[str, object], args_json: str, confirmed: bool, provider: str, model: str) -> dict[str, object]:
    tool_spec = definition.get("tool")
    if not isinstance(tool_spec, dict):
        raise ValueError("tool definition is missing tool spec")
    if str(tool_spec.get("type") or "").lower() != "external-command":
        raise ValueError(f"Unsupported tool type: {tool_spec.get('type')}")

    try:
        raw_args = normalize_args(load_json(args_json))
    except Exception as exc:
        raise ValueError(f"Failed to parse --args-json={args_json!r}: {exc}") from exc
    args = apply_defaults(definition, raw_args)
    missing = missing_required_args(definition, args)
    if missing:
        filled = try_fill_missing_args(definition, missing, args, provider, model)
        args = apply_defaults(definition, filled)
        missing = missing_required_args(definition, args)
        if missing:
            return {
                "ok": False,
                "toolName": tool_name,
                "output": "",
                "data": {"missingArgs": missing},
                "error": "Missing required arguments: " + ", ".join(missing),
            }

    if not bool(tool_spec.get("autoExecuteAllowed", False)) and not confirmed:
        return {
            "ok": False,
            "toolName": tool_name,
            "output": "",
            "data": {},
            "error": "Tool requires confirmation. Re-run with --confirm to execute.",
        }

    scripts_dir = runtime_dir().parent
    command: list[str] = []
    program = str(tool_spec.get("program") or "")
    if not program:
        raise ValueError("Missing external command program")
    command.extend(resolve_launcher(program))
    for token in tool_spec.get("args") or []:
        rendered = render_token(str(token), args, tool_home, scripts_dir)
        if rendered:
            command.append(rendered)

    completed = subprocess.run(command, cwd=str(tool_home), capture_output=True, text=True)
    stdout = (completed.stdout or "").strip()
    stderr = (completed.stderr or "").strip()
    if completed.returncode != 0:
        return {
            "ok": False,
            "toolName": tool_name,
            "output": "",
            "data": {"command": command, "exitCode": completed.returncode},
            "error": stderr or f"tool failed with exit code {completed.returncode}",
        }

    if stdout.startswith("{") and stdout.endswith("}"):
        try:
            envelope = load_json(stdout)
            if isinstance(envelope, dict) and "ok" in envelope and "toolName" in envelope:
                return envelope
        except Exception:
            pass

    return {
        "ok": True,
        "toolName": tool_name,
        "output": stdout,
        "data": {"command": command, "exitCode": completed.returncode},
        "error": "",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Python tool dispatcher.")
    parser.add_argument("--mode", default="execute")
    parser.add_argument("--tool", required=True)
    parser.add_argument("--args-json", default="")
    parser.add_argument("--instruction", default="")
    parser.add_argument("--tools-dir", default="")
    parser.add_argument("--provider", default="")
    parser.add_argument("--model", default="")
    parser.add_argument("--confirm", action="store_true")
    args = parser.parse_args()

    tools_dir = resolve_tools_dir(args.tools_dir)
    try:
        if args.mode == "dispatch":
            envelope = dispatch_capability(
                normalize_optional(args.instruction) or "",
                tools_dir,
                normalize_optional(args.provider) or os.getenv("AI_FIX_PROVIDER") or "",
                normalize_optional(args.model) or os.getenv("AI_FIX_MODEL") or "",
                args.confirm,
            )
            if envelope.get("ok"):
                emit_result(success(
                    str(envelope.get("toolName") or args.tool),
                    str(envelope.get("output") or ""),
                    envelope.get("data") if isinstance(envelope.get("data"), dict) else {},
                ))
                return
            emit_result(failure(str(envelope.get("toolName") or args.tool), str(envelope.get("error") or "Execution failed")))
            raise SystemExit(1)
        tool_home = tools_dir / args.tool
        if not tool_home.is_dir():
            emit_result(failure(args.tool, f"Unknown tool: {args.tool}"))
            raise SystemExit(1)
        try:
            definition = load_tool_definition(tool_home)
        except Exception as exc:
            raise ValueError(f"Failed to load tool definition: {exc}") from exc
        args_json = normalize_optional(args.args_json)
        if not args_json:
            if sys.stdin.isatty():
                args_json = "{}"
            else:
                stdin_payload = sys.stdin.read()
                args_json = stdin_payload.strip() if stdin_payload and stdin_payload.strip() else "{}"
        envelope = execute_external(
            args.tool,
            tool_home,
            definition,
            args_json,
            args.confirm,
            normalize_optional(args.provider) or os.getenv("AI_FIX_PROVIDER") or "",
            normalize_optional(args.model) or os.getenv("AI_FIX_MODEL") or "",
        )
        if envelope.get("ok"):
            emit_result(success(
                str(envelope.get("toolName") or args.tool),
                str(envelope.get("output") or ""),
                envelope.get("data") if isinstance(envelope.get("data"), dict) else {},
            ))
            return
        emit_result(failure(str(envelope.get("toolName") or args.tool), str(envelope.get("error") or "Execution failed")))
        raise SystemExit(1)
    except Exception as exc:
        emit_result(failure(args.tool, str(exc)))
        raise SystemExit(1)


if __name__ == "__main__":
    main()

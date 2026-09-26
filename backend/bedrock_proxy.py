"""Steady's Bedrock proxy.

Why this exists at all: Steady is a native Android app with no server of its own.
Calling AWS Bedrock directly from the app would mean shipping a long-lived AWS secret
key inside the APK, which is a real security bug, not a hackathon shortcut. This tiny
local proxy keeps the AWS credentials on the machine that owns them (this dev machine's
`~/.aws/credentials`, picked up by boto3's default credential chain) and gives the
Android emulator a plain HTTP endpoint to call instead, reachable at 10.0.2.2 (the
emulator's alias for the host loopback interface). In a real deployment this process
would be a small Lambda or Cloud Run-style service; here it is the same idea running on
localhost so the demo does not need a cloud account of its own.

Two endpoints, matching the two places SPEC.md says a model is allowed to touch this
product, and nowhere else:

  POST /summarize            -- write the weekly caregiver update from facts the rules
                                 already computed (ProgramEngine, AdherenceTracker).
                                 Returns {"text": "..."}. The app runs SummaryGuard over
                                 this before it is shown or stored.
  POST /choose-explanation   -- pick which of the app's OWN sentences explains today's
                                 session. Returns {"choice": <int>} and nothing else.

The asymmetry is the point. The summary describes a record to a family member who will
not act on it physically, so prose is appropriate and is guarded. The explanation
appears on the exercise screen in front of somebody about to stand up, so the model does
not get to write it: the candidate sentences come from the app (AdjustmentPhrasings.kt),
the model returns an index, and the app renders its own string. There is no free text on
that path to validate, which is a stronger property than validating it well.

Neither endpoint is ever allowed to invent a number, change a decision, or sit on the
path that decides what exercise the person sees next.

Run: ./venv/bin/python bedrock_proxy.py
"""

from __future__ import annotations

import json
import logging
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import boto3

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s")
log = logging.getLogger("bedrock_proxy")

# In preference order. `anthropic.claude-sonnet-5` and `anthropic.claude-opus-5`
# appear in ListFoundationModels for this account but return AccessDeniedException
# from InvokeModel; only the inference-profile ids below are actually callable, and
# that gap is exactly why this is a chain and not one hard-coded id: a model id that
# lists but cannot be invoked should degrade to the next preference, never take the
# whole feature down.
MODEL_ID_PREFERENCE = [
    "us.anthropic.claude-sonnet-4-5-20250929-v1:0",
    "us.anthropic.claude-sonnet-4-6",
]
REGION = "us-east-1"
PORT = 8765

_bedrock = boto3.client("bedrock-runtime", region_name=REGION)


def _call_claude(system_prompt: str, user_prompt: str, max_tokens: int = 220) -> str:
    body = {
        "anthropic_version": "bedrock-2023-05-31",
        "max_tokens": max_tokens,
        "system": system_prompt,
        "messages": [{"role": "user", "content": user_prompt}],
    }
    last_error: Exception | None = None
    for model_id in MODEL_ID_PREFERENCE:
        try:
            response = _bedrock.invoke_model(
                modelId=model_id,
                body=json.dumps(body),
                contentType="application/json",
                accept="application/json",
            )
            payload = json.loads(response["body"].read())
            parts = payload.get("content", [])
            text = "".join(p.get("text", "") for p in parts if p.get("type") == "text")
            return text.strip()
        except Exception as exc:  # noqa: BLE001 - try the next preference, not crash
            log.warning("Model %s failed (%s), trying next preference", model_id, exc)
            last_error = exc
    raise last_error or RuntimeError("no model preference configured")


SUMMARY_SYSTEM_PROMPT = (
    "You write one short weekly update for a family member about a relative's "
    "falls-prevention exercise programme, called Steady. You are given the exact "
    "facts already computed by the app's own rules: a session count, a prescribed "
    "dose, a streak, and a difficulty tier. Use only the facts given to you. Never "
    "invent a number, a date, or a fact not in the input. Never use clinical or "
    "alarming language: a week with zero sessions is a plain fact, never a failure, "
    "never 'missed' or 'non-compliant'. Write two to three sentences, warm and plain, "
    "as if a thoughtful person wrote a text message. No greetings-card language, no "
    "exclamation marks, no emoji."
)

CHOICE_SYSTEM_PROMPT = (
    "You choose the friendliest wording for a person doing a falls-prevention exercise "
    "session on their television. You are given what the app's own rules did to today's "
    "session, and a numbered list of sentences the app has already written. Reply with "
    "the index of the single best sentence and nothing else: a bare integer, no words, "
    "no punctuation, no explanation. You may not write a sentence of your own; anything "
    "that is not an index in range is discarded and the first option is used."
)


def summarize(payload: dict) -> str:
    facts = (
        f"family member's first name: {payload.get('familyMemberName', 'there')}\n"
        f"programme week number: {payload.get('weekNumber')}\n"
        f"sessions completed this week: {payload.get('sessionsCompletedThisWeek')}\n"
        f"sessions prescribed this week: {payload.get('prescribedPerWeek')}\n"
        f"current streak in weeks: {payload.get('streakWeeks')}\n"
        f"current difficulty tier: {payload.get('tier')}\n"
    )
    return _call_claude(SUMMARY_SYSTEM_PROMPT, facts)


def choose_explanation(payload: dict) -> int:
    """Return an index into the caller's own option list, or 0.

    The app bounds-checks this again on its side, so a bad answer here is harmless
    rather than merely unlikely. Parsing is deliberately strict: the first integer in
    the reply, and only if the reply is short enough to plausibly be an index.
    """
    options = payload.get("options") or []
    if not isinstance(options, list) or not options:
        return 0
    listing = "\n".join(f"{i}: {opt}" for i, opt in enumerate(options))
    facts = (
        f"what the rules did to today's session: {payload.get('kind')}\n"
        f"in plain terms: {payload.get('adjustmentDescription')}\n"
        f"sentences to choose between:\n{listing}\n"
        f"Reply with one integer between 0 and {len(options) - 1}."
    )
    raw = _call_claude(CHOICE_SYSTEM_PROMPT, facts, max_tokens=8)
    digits = "".join(c for c in raw if c.isdigit())[:4]
    if not digits:
        return 0
    index = int(digits)
    return index if 0 <= index < len(options) else 0


class Handler(BaseHTTPRequestHandler):
    def _send_json(self, status: int, obj: dict) -> None:
        body = json.dumps(obj).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802 (BaseHTTPRequestHandler API)
        if self.path == "/health":
            self._send_json(200, {"status": "ok"})
        else:
            self._send_json(404, {"error": "not found"})

    def do_POST(self) -> None:  # noqa: N802
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            self._send_json(400, {"error": "invalid json"})
            return

        try:
            if self.path == "/summarize":
                self._send_json(200, {"text": summarize(payload)})
            elif self.path == "/choose-explanation":
                self._send_json(200, {"choice": choose_explanation(payload)})
            else:
                self._send_json(404, {"error": "not found"})
        except Exception:  # noqa: BLE001 - a demo proxy must never 500-crash silently
            # The detail stays here, in this process's log, on the machine that owns the
            # AWS credentials. It does not go back over the wire: a boto3 exception
            # string routinely carries the account's own ARN, and handing the caller our
            # AWS account identity to explain a failed call is not a trade worth making.
            # The app treats any non-2xx as "no model answered" and uses its
            # deterministic text, so a generic message costs it nothing.
            log.exception("Bedrock call failed")
            self._send_json(502, {"error": "narration unavailable"})

    def log_message(self, fmt: str, *args) -> None:  # quieter default logging
        log.info("%s - %s", self.address_string(), fmt % args)


# Loopback only. This process holds real AWS credentials and authenticates nobody, so
# anything that can reach it can spend from this account. 10.0.2.2 is the Android
# emulator's alias for the host's loopback interface, so binding here is what the
# emulator needs and nothing more; a device on the same wifi is not meant to reach it,
# and on 0.0.0.0 it could. This also makes the code agree with what the README promises,
# which it previously did not.
BIND_HOST = "127.0.0.1"


def main() -> None:
    server = ThreadingHTTPServer((BIND_HOST, PORT), Handler)
    log.info("Steady Bedrock proxy listening on %s:%s (models=%s)", BIND_HOST, PORT, MODEL_ID_PREFERENCE)
    server.serve_forever()


if __name__ == "__main__":
    main()

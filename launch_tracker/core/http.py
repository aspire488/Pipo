"""HTTP client with retries, exponential backoff and rate-limit awareness.

Only stdlib. Nothing here executes remote code, and no token is ever logged:
headers are stored but `Response` never carries the request headers.
"""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field

USER_AGENT = "PipoLaunchIntel/1.0 (+https://github.com/aspire488/Pipo; launch monitoring)"

RETRY_STATUS = (429, 500, 502, 503, 504)
MAX_BYTES = 4_000_000


@dataclass
class Response:
    url: str
    status: int | None
    body: bytes = b""
    headers: dict = field(default_factory=dict)
    error: str | None = None
    elapsed_ms: int = 0
    attempts: int = 1

    @property
    def ok(self) -> bool:
        return self.status is not None and 200 <= self.status < 300

    @property
    def rate_remaining(self):
        value = self.headers.get("ratelimit-remaining") or self.headers.get("x-ratelimit-remaining")
        try:
            return int(value)
        except (TypeError, ValueError):
            return None

    def json(self):
        try:
            return json.loads(self.body.decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            return None

    def text(self) -> str:
        return self.body.decode("utf-8", "replace")

    def summary(self) -> str:
        return "HTTP %s" % self.status if self.error is None else "%s (%s)" % (self.error, self.attempts)


class HttpClient:
    """Small GET-only client. Retries transient failures, never 4xx except 429."""

    def __init__(self, timeout: int = 20, retries: int = 2, backoff: float = 1.5,
                 user_agent: str = USER_AGENT, headers: dict | None = None,
                 sleep=time.sleep, max_bytes: int = MAX_BYTES):
        self.timeout = timeout
        self.retries = max(0, int(retries))
        self.backoff = backoff
        self.user_agent = user_agent
        self.headers = dict(headers or {})
        self._sleep = sleep
        self.max_bytes = max_bytes
        self.log: list = []

    def _delay(self, response: Response, attempt: int) -> float:
        retry_after = response.headers.get("retry-after")
        if retry_after:
            try:
                return min(60.0, max(0.0, float(retry_after)))
            except ValueError:
                pass
        return min(30.0, self.backoff ** attempt)

    def get(self, url: str, headers: dict | None = None, accept: str = "application/json") -> Response:
        merged = {"User-Agent": self.user_agent, "Accept": accept}
        merged.update(self.headers)
        merged.update(headers or {})

        last = Response(url, None)
        for attempt in range(1, self.retries + 2):
            started = time.time()
            try:
                request = urllib.request.Request(url, headers=merged)
                with urllib.request.urlopen(request, timeout=self.timeout) as raw:
                    body = raw.read(self.max_bytes)
                    last = Response(url, raw.status, body,
                                    {k.lower(): v for k, v in raw.headers.items()},
                                    None, int((time.time() - started) * 1000), attempt)
                    return last
            except urllib.error.HTTPError as exc:
                body = b""
                try:
                    body = exc.read(self.max_bytes)
                except Exception:  # noqa: BLE001 - body may be unavailable
                    pass
                last = Response(url, exc.code, body,
                                {k.lower(): v for k, v in (exc.headers or {}).items()},
                                "HTTP %d" % exc.code, int((time.time() - started) * 1000), attempt)
                if exc.code in RETRY_STATUS and attempt <= self.retries:
                    delay = self._delay(last, attempt)
                    self.log.append({"url": url, "status": exc.code, "retry_in": delay})
                    self._sleep(delay)
                    continue
                return last
            except Exception as exc:  # noqa: BLE001 - network layer must not raise
                last = Response(url, None, b"", {}, str(exc)[:200],
                                int((time.time() - started) * 1000), attempt)
                if attempt <= self.retries:
                    delay = min(30.0, self.backoff ** attempt)
                    self.log.append({"url": url, "error": str(exc)[:120], "retry_in": delay})
                    self._sleep(delay)
                    continue
                return last
        return last

    def get_json(self, url: str, headers: dict | None = None):
        response = self.get(url, headers=headers)
        return response.json(), response

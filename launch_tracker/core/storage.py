"""Durable local storage: atomic JSON writes, append-only JSONL, run lock.

Everything the tracker knows lives under launch_tracker/data (git-ignored).
Writes are atomic (temp file + os.replace) so a killed run cannot leave a
half-written snapshot behind.
"""

from __future__ import annotations

import json
import os
import time
from contextlib import contextmanager
from pathlib import Path

from .model import utc_now

LOCK_STALE_SECONDS = 900


class Store:
    def __init__(self, root: Path):
        self.root = Path(root)
        self.data = self.root / "data"
        self.history_dir = self.data / "history"
        self.events_dir = self.data / "events"
        self.manual_dir = self.data / "manual"
        self.reports_dir = self.root / "reports"
        self.latest_path = self.data / "latest.json"
        self.history_path = self.history_dir / "snapshots.jsonl"
        self.events_path = self.events_dir / "events.jsonl"
        self.milestones_path = self.data / "milestones.json"
        self.notifications_log = self.data / "notifications.log"
        self.deliveries_dir = self.data / "notifications"
        self.deliveries_path = self.deliveries_dir / "deliveries.jsonl"
        self.digest_path = self.data / "digest.json"
        self.lock_path = self.data / ".lock"
        self.ready = False

    # -- setup ------------------------------------------------------------
    def ensure(self) -> "Store":
        for directory in (self.data, self.history_dir, self.events_dir, self.manual_dir,
                          self.reports_dir, self.deliveries_dir):
            directory.mkdir(parents=True, exist_ok=True)
        self.ready = True
        return self

    @staticmethod
    def atomic_write(path: Path, text: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_name(".%s.tmp" % path.name)
        with open(tmp, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(text)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp, path)

    # -- latest -----------------------------------------------------------
    def save_latest(self, snapshot: dict) -> None:
        self.ensure()
        self.atomic_write(self.latest_path, json.dumps(snapshot, indent=2, ensure_ascii=False) + "\n")

    def load_latest(self) -> dict | None:
        if not self.latest_path.exists():
            return None
        try:
            return json.loads(self.latest_path.read_text(encoding="utf-8"))
        except (ValueError, OSError):
            return None

    # -- history ----------------------------------------------------------
    def append_history(self, snapshot: dict) -> None:
        self.ensure()
        line = json.dumps(snapshot, ensure_ascii=False)
        with open(self.history_path, "a", encoding="utf-8", newline="\n") as handle:
            handle.write(line + "\n")

    def history(self, limit: int | None = None) -> list:
        return _read_jsonl(self.history_path, limit)

    # -- events -----------------------------------------------------------
    def events(self, limit: int | None = None) -> list:
        return _read_jsonl(self.events_path, limit)

    def known_event_ids(self) -> set:
        return {event.get("event_id") for event in self.events() if event.get("event_id")}

    def append_events(self, events: list) -> list:
        """Append only events whose id was never stored. Returns what was written."""
        if not events:
            return []
        self.ensure()
        known = self.known_event_ids()
        fresh = []
        for event in events:
            event_id = event.get("event_id")
            if not event_id or event_id in known:
                continue
            known.add(event_id)
            fresh.append(event)
        if fresh:
            with open(self.events_path, "a", encoding="utf-8", newline="\n") as handle:
                for event in fresh:
                    handle.write(json.dumps(event, ensure_ascii=False) + "\n")
        return fresh

    # -- milestones -------------------------------------------------------
    def load_milestones(self) -> dict:
        if not self.milestones_path.exists():
            return {"achieved": {}}
        try:
            data = json.loads(self.milestones_path.read_text(encoding="utf-8"))
        except (ValueError, OSError):
            return {"achieved": {}}
        if not isinstance(data, dict):
            return {"achieved": {}}
        data.setdefault("achieved", {})
        return data

    def save_milestones(self, data: dict) -> None:
        self.ensure()
        self.atomic_write(self.milestones_path, json.dumps(data, indent=2, ensure_ascii=False) + "\n")

    # -- manual observations ---------------------------------------------
    def load_manual(self, name: str) -> dict:
        path = self.manual_dir / ("%s.json" % name)
        if not path.exists():
            return {}
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (ValueError, OSError):
            return {}
        return data if isinstance(data, dict) else {}

    # -- notifications log ------------------------------------------------
    def log_notification(self, line: str) -> None:
        self.ensure()
        with open(self.notifications_log, "a", encoding="utf-8", newline="\n") as handle:
            handle.write("%s %s\n" % (utc_now(), line))

    # -- notification delivery records ------------------------------------
    def deliveries(self, limit: int | None = None) -> list:
        return _read_jsonl(self.deliveries_path, limit)

    def append_delivery(self, record: dict) -> None:
        self.ensure()
        with open(self.deliveries_path, "a", encoding="utf-8", newline="\n") as handle:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")

    def notified_event_ids(self, channel: str = "email") -> set:
        """Only deliveries the provider accepted count as notified."""
        return {record.get("event_id") for record in self.deliveries()
                if record.get("channel") == channel and record.get("status") == "sent"}

    def load_digest_state(self) -> dict:
        if not self.digest_path.exists():
            return {}
        try:
            data = json.loads(self.digest_path.read_text(encoding="utf-8"))
        except (ValueError, OSError):
            return {}
        return data if isinstance(data, dict) else {}

    def save_digest_state(self, data: dict) -> None:
        self.ensure()
        self.atomic_write(self.digest_path, json.dumps(data, indent=2, ensure_ascii=False) + "\n")

    # -- concurrency ------------------------------------------------------
    @contextmanager
    def lock(self, wait_seconds: float = 0.0):
        """Prevent two runs from interleaving writes. Stale locks self-heal."""
        self.ensure()
        acquired = False
        deadline = time.time() + max(0.0, wait_seconds)
        while True:
            try:
                handle = os.open(self.lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
                os.write(handle, ("%d %s" % (os.getpid(), utc_now())).encode())
                os.close(handle)
                acquired = True
                break
            except FileExistsError:
                try:
                    age = time.time() - self.lock_path.stat().st_mtime
                except OSError:
                    age = 0
                if age > LOCK_STALE_SECONDS:
                    try:
                        self.lock_path.unlink()
                    except OSError:
                        pass
                    continue
                if time.time() >= deadline:
                    break
                time.sleep(0.5)
        try:
            yield acquired
        finally:
            if acquired:
                try:
                    self.lock_path.unlink()
                except OSError:
                    pass


def _read_jsonl(path: Path, limit: int | None = None) -> list:
    if not path.exists():
        return []
    records = []
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                records.append(json.loads(line))
            except ValueError:
                continue
    return records[-limit:] if limit else records

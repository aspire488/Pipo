"""Adapter contract shared by every platform collector."""

from __future__ import annotations


class Adapter:
    """Every adapter is a module exposing NAME and collect(client, config, **kw)."""

    NAME = "base"

    @staticmethod
    def collect(client, config, **kwargs):  # pragma: no cover - interface only
        raise NotImplementedError


def section(config: dict, name: str) -> dict:
    value = config.get(name)
    return value if isinstance(value, dict) else {}


def env_first(*names: str):
    """First non-empty environment variable, without ever logging values."""
    import os

    for name in names:
        value = os.environ.get(name)
        if value:
            return value
    return None

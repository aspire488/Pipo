"""Adapter registry. Adding a platform means adding a module here plus config."""

from . import fdroid, freebuff, github, hackernews, product_hunt, reddit, website, xerj

ORDER = ["github", "fdroid", "reddit", "hackernews", "product_hunt", "freebuff", "website", "xerj"]

MODULES = {
    "github": github,
    "fdroid": fdroid,
    "reddit": reddit,
    "hackernews": hackernews,
    "product_hunt": product_hunt,
    "freebuff": freebuff,
    "website": website,
    "xerj": xerj,
}


def get(name: str):
    return MODULES.get(name)

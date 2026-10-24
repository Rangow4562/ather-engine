from functools import cache

import ather_core


@cache
def get_session_context(
    manifest_str: str | None, function_path: str, properties: frozenset | None = None
) -> ather_core.SessionContext:
    return ather_core.SessionContext(manifest_str, function_path, properties)


def get_manifest_extractor(manifest_str: str) -> ather_core.ManifestExtractor:
    return ather_core.ManifestExtractor(manifest_str)


def to_json_base64(manifest) -> str:
    return ather_core.to_json_base64(manifest)

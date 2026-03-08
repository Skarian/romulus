from __future__ import annotations

import base64
import json
import sys
from pathlib import Path
from urllib.parse import parse_qs, urlparse
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def read_env(env_path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in env_path.read_text().splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] == '"':
            value = value[1:-1]
        values[key.strip()] = value
    return values


def extract_info_hash(magnet: str) -> str:
    query = parse_qs(urlparse(magnet).query)
    xt_values = query.get("xt", [])
    btih = next((value for value in xt_values if value.lower().startswith("urn:btih:")), None)
    if btih is None:
        raise SystemExit("Spike 1 magnet is missing xt=urn:btih")
    raw = btih.split(":", 2)[-1].strip()
    if len(raw) == 40 and all(character in "0123456789abcdefABCDEF" for character in raw):
        return raw.lower()
    if len(raw) == 32:
        decoded = base64.b32decode(raw.upper()).hex()
        return decoded[:40].lower()
    raise SystemExit("Spike 1 magnet has unsupported btih format")


def collect_spike1_hashes(env: dict[str, str]) -> list[str]:
    magnets = [
        env.get("SPIKE1_MAGNET", "").strip(),
        env.get("SPIKE1_UNCACHED_MAGNET", "").strip(),
    ]
    hashes: list[str] = []
    seen: set[str] = set()
    for magnet in magnets:
        if not magnet:
            continue
        info_hash = extract_info_hash(magnet)
        if info_hash in seen:
            continue
        seen.add(info_hash)
        hashes.append(info_hash)
    return hashes


def api_request(token: str, method: str, path: str) -> tuple[int, object | None]:
    request = Request(
        f"https://api.real-debrid.com/rest/1.0{path}",
        method=method,
        headers={"Authorization": f"Bearer {token}"},
    )
    try:
        with urlopen(request) as response:
            body = response.read().decode("utf-8").strip()
            return response.status, json.loads(body) if body else None
    except HTTPError as error:
        body = error.read().decode("utf-8").strip()
        parsed = json.loads(body) if body else None
        return error.code, parsed


def list_matching_torrents(token: str, info_hash: str) -> list[dict[str, object]]:
    matches: list[dict[str, object]] = []
    page = 1
    while True:
        status, payload = api_request(token, "GET", f"/torrents?page={page}&limit=100")
        if status != 200:
            raise SystemExit(f"Failed to list torrents: HTTP {status}")
        if not isinstance(payload, list):
            raise SystemExit("Unexpected torrents response shape")
        for item in payload:
            if isinstance(item, dict) and str(item.get("hash", "")).lower() == info_hash:
                matches.append(item)
        if len(payload) < 100:
            return matches
        page += 1


def delete_torrent(token: str, torrent_id: str) -> None:
    status, _ = api_request(token, "DELETE", f"/torrents/delete/{torrent_id}")
    if status in {200, 204, 404}:
        return
    raise SystemExit(f"Failed deleting torrent {torrent_id}: HTTP {status}")


def main() -> None:
    env_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".env.local")
    if not env_path.is_file():
        raise SystemExit(f"Missing env file: {env_path}")

    env = read_env(env_path)
    token = env.get("RD_API_TOKEN", "").strip()
    if not token:
        raise SystemExit("Missing RD_API_TOKEN in env file")
    info_hashes = collect_spike1_hashes(env)
    if not info_hashes:
        raise SystemExit("Missing SPIKE1_MAGNET and SPIKE1_UNCACHED_MAGNET in env file")

    all_matches: dict[str, dict[str, object]] = {}
    per_hash_counts: list[tuple[str, int]] = []
    for info_hash in info_hashes:
        matches = list_matching_torrents(token, info_hash)
        per_hash_counts.append((info_hash, len(matches)))
        for item in matches:
            torrent_id = str(item.get("id", "")).strip()
            if torrent_id:
                all_matches[torrent_id] = item

    if not all_matches:
        for info_hash, _ in per_hash_counts:
            print(f"No Real-Debrid torrents found for hash {info_hash}")
        return

    print(f"Deleting {len(all_matches)} Real-Debrid torrent(s) for current Spike 1 hash set")
    for info_hash, count in per_hash_counts:
        print(f"- hash {info_hash}: {count} match(es)")
    for torrent_id in sorted(all_matches):
        delete_torrent(token, torrent_id)
        print(f"- deleted {torrent_id}")


if __name__ == "__main__":
    main()

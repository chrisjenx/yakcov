#!/usr/bin/env python3
"""Track upstream Compose Multiplatform versions and update compose-releases.toml.

Each channel in compose-releases.toml is mapped to an upstream "track":
  - track = "stable"     -> follow the latest stable Compose release
  - track = "prerelease" -> follow the latest alpha/beta/rc Compose release
If a channel omits `track`, the section named "stable" defaults to "stable" and
every other section defaults to "prerelease".

Data sources (stdlib only, no third-party deps):
  - Compose version   : Maven Central metadata for org.jetbrains.compose:runtime
                        (its artifact version is the Compose Multiplatform version)
  - Material3 version : Maven Central metadata for org.jetbrains.compose.material3:material3
                        (highest version sharing the compose major.minor)
  - Kotlin (best-effort): JetBrains/compose-multiplatform GitHub release notes for the
                        compose tag, falling back to the minor's vX.Y.0 GA notes.
                        Only applied to channels that already declare a `kotlin` key.

Modes:
  --check                Print per-channel drift. Exit 0 if up to date, 3 if drift.
  --write                Rewrite compose-releases.toml in place with desired versions.
  --pr-body PATH         Write a markdown summary (for a PR body) to PATH.
  --self-test            Run offline unit tests against fixtures (no network).

--check / --write hit the network. --self-test never does.
"""

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request

TOML_PATH = "compose-releases.toml"
MAVEN_BASE = "https://repo1.maven.org/maven2"
GH_RELEASES = "https://api.github.com/repos/JetBrains/compose-multiplatform/releases/tags"
UA = "yakcov-compose-tracker"
TIMEOUT = 30

# --- Version parsing & ordering -------------------------------------------------

# Pre-release stage ordering: dev < alpha < beta < rc < (stable).
_PRE_RANK = {"dev": 0, "alpha": 1, "beta": 2, "rc": 3}
_STABLE_RANK = 4

_VERSION_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)(?:-([A-Za-z]+)(\d+)?(?:\.(\d+))?)?$")


def parse_version(v):
    """Return a sortable tuple for a Compose/Material3 version string.

    Handles 'MAJOR.MINOR.PATCH' and 'MAJOR.MINOR.PATCH-stageNN[.extra]'
    (e.g. alpha05, rc01, beta02, dev2099, alpha02.1). Unknown shapes sort lowest.
    """
    m = _VERSION_RE.match(v)
    if not m:
        return (-1, -1, -1, -1, -1, -1, v)
    major, minor, patch = int(m.group(1)), int(m.group(2)), int(m.group(3))
    stage = m.group(4)
    stage_num = int(m.group(5)) if m.group(5) else 0
    extra = int(m.group(6)) if m.group(6) else 0
    rank = _STABLE_RANK if stage is None else _PRE_RANK.get(stage.lower(), -1)
    return (major, minor, patch, rank, stage_num, extra, "")


def is_stable(v):
    return parse_version(v)[3] == _STABLE_RANK


def latest_stable(versions):
    stable = [v for v in versions if is_stable(v)]
    return max(stable, key=parse_version) if stable else None


def latest_prerelease(versions):
    pre = [v for v in versions if parse_version(v)[3] in _PRE_RANK.values()]
    return max(pre, key=parse_version) if pre else None


def major_minor(v):
    pv = parse_version(v)
    return (pv[0], pv[1])


def match_material3(compose_version, m3_versions):
    """Highest Material3 version sharing the compose version's major.minor."""
    if not compose_version:
        return None
    mm = major_minor(compose_version)
    candidates = [m for m in m3_versions if major_minor(m) == mm]
    return max(candidates, key=parse_version) if candidates else None


# --- Kotlin best-effort parsing -------------------------------------------------

def parse_kotlin_from_notes(text):
    """Extract a Kotlin x.y.z version from Compose release notes.

    Only high-confidence phrasings ("Kotlin X is required", "based on Kotlin X") are
    accepted. A bare "Kotlin X.Y.Z" mention is deliberately NOT matched: release notes
    frequently cite a minimum/compatibility or historical version, so a loose match
    returns the wrong toolchain. When confidence is low we return None and the caller
    keeps the current value and flags it for manual verification.
    """
    if not text:
        return None
    for pat in (
        r"Kotlin\s+(\d+\.\d+\.\d+)\s+is\s+required",
        r"based on\s+Kotlin\s+(\d+\.\d+\.\d+)",
    ):
        m = re.search(pat, text, re.IGNORECASE)
        if m:
            return m.group(1)
    return None


def resolve_kotlin(compose_version, notes_fetcher):
    """Try the exact compose tag, then the minor's GA tag (vX.Y.0). Returns (kotlin, tag)."""
    mm = major_minor(compose_version)
    candidate_tags = ["v" + compose_version, "v%d.%d.0" % (mm[0], mm[1])]
    seen = set()
    for tag in candidate_tags:
        if tag in seen:
            continue
        seen.add(tag)
        notes = notes_fetcher(tag)
        if notes:
            kotlin = parse_kotlin_from_notes(notes)
            if kotlin:
                return kotlin, tag
    return None, None


# --- TOML read / rewrite (flat string values only) ------------------------------

def parse_toml_channels(text):
    """Return {channel: {key: value}} from compose-releases.toml."""
    channels = {}
    current = None
    for line in text.split("\n"):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        section = re.match(r"^\[([^\]]+)\]$", stripped)
        if section:
            current = section.group(1).strip()
            channels[current] = {}
            continue
        if current is not None:
            kv = re.match(r'^([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"', stripped)
            if kv:
                channels[current][kv.group(1)] = kv.group(2)
    return channels


def rewrite_channel_key(text, channel, key, value):
    """Set channel.key = "value", preserving formatting. Returns (new_text, changed)."""
    out = []
    in_section = False
    changed = False
    key_re = re.compile(r'^(\s*' + re.escape(key) + r'\s*=\s*)"([^"]*)"(\s*(?:#.*)?)$')
    for line in text.split("\n"):
        section = re.match(r"^\s*\[([^\]]+)\]\s*$", line)
        if section:
            in_section = section.group(1).strip() == channel
            out.append(line)
            continue
        if in_section:
            kv = key_re.match(line)
            if kv:
                if kv.group(2) != value:
                    changed = True
                out.append('%s"%s"%s' % (kv.group(1), value, kv.group(3)))
                continue
        out.append(line)
    return "\n".join(out), changed


# --- Update computation ---------------------------------------------------------

def _track_for(name, cfg):
    track = cfg.get("track")
    if track is None:
        return "stable" if name == "stable" else "prerelease"
    if track not in ("stable", "prerelease"):
        raise ValueError('Channel [%s] has invalid track=%r (expected "stable" or "prerelease").'
                         % (name, track))
    return track


def compute_updates(channels, compose_versions, m3_versions, notes_fetcher):
    """Compute current->desired versions per channel. Pure (network via notes_fetcher)."""
    tracks = {name: _track_for(name, cfg) for name, cfg in channels.items()}
    # Reference for the prerelease guard: the latest stable release upstream, computed
    # globally so the guard still applies even when no channel itself tracks stable.
    max_stable = latest_stable(compose_versions)

    updates = {}
    for name, cfg in channels.items():
        track = tracks[name]
        flags = []
        cur_compose = cfg.get("compose")

        if track == "stable":
            desired_compose = latest_stable(compose_versions)
        else:
            desired_compose = latest_prerelease(compose_versions)
            if (desired_compose and max_stable
                    and parse_version(desired_compose) <= parse_version(max_stable)):
                flags.append(
                    "No prerelease newer than stable %s — keeping current compose %s."
                    % (max_stable, cur_compose))
                desired_compose = cur_compose
        if not desired_compose:
            flags.append("Could not determine desired compose version — keeping current.")
            desired_compose = cur_compose
        elif cur_compose and parse_version(desired_compose) < parse_version(cur_compose):
            # Never downgrade: a withdrawn/yanked upstream release must not drag a channel back.
            flags.append("Latest upstream %s is older than current %s — keeping current "
                         "(possible upstream yank)." % (desired_compose, cur_compose))
            desired_compose = cur_compose

        cur_m3 = cfg.get("compose-material3")
        desired_m3 = match_material3(desired_compose, m3_versions)
        if not desired_m3:
            line = ".".join(map(str, major_minor(desired_compose))) if desired_compose else "?"
            flags.append("No Material3 published for the %s line — keeping current %s."
                         % (line, cur_m3))
            desired_m3 = cur_m3

        kotlin_managed = "kotlin" in cfg
        cur_kotlin = cfg.get("kotlin")
        desired_kotlin = cur_kotlin
        if kotlin_managed and desired_compose:
            kotlin, tag = resolve_kotlin(desired_compose, notes_fetcher)
            if kotlin:
                desired_kotlin = kotlin
                if kotlin != cur_kotlin:
                    flags.append("Kotlin %s parsed from %s notes — VERIFY before merge."
                                 % (kotlin, tag))
            else:
                flags.append("Could not determine Kotlin for %s — keeping %s; verify manually."
                             % (desired_compose, cur_kotlin))

        changed = (desired_compose != cur_compose
                   or desired_m3 != cur_m3
                   or (kotlin_managed and desired_kotlin != cur_kotlin))

        updates[name] = {
            "track": track,
            "compose": {"current": cur_compose, "desired": desired_compose},
            "compose-material3": {"current": cur_m3, "desired": desired_m3},
            "kotlin": {"current": cur_kotlin, "desired": desired_kotlin, "managed": kotlin_managed},
            "flags": flags,
            "changed": changed,
        }
    return updates


def apply_updates(text, updates):
    """Apply desired versions to TOML text.

    Raises RuntimeError if an intended change cannot be written (the key line is
    missing or in a form rewrite_channel_key can't match), so the file never silently
    diverges from the reported drift. Returns (new_text, any_changed).
    """
    new_text = text
    any_changed = False
    for name, u in updates.items():
        fields = ["compose", "compose-material3"]
        if u["kotlin"]["managed"]:
            fields.append("kotlin")
        for field in fields:
            desired, current = u[field]["desired"], u[field]["current"]
            if desired is not None and desired != current:
                new_text, changed = rewrite_channel_key(new_text, name, field, desired)
                if not changed:
                    raise RuntimeError(
                        "Cannot write [%s] %s = %r: no matching line in %s "
                        "(key missing or malformed)." % (name, field, desired, TOML_PATH))
                any_changed = True
    return new_text, any_changed


# --- Reporting ------------------------------------------------------------------

def _diff_rows(updates):
    rows = []
    for name in sorted(updates):
        u = updates[name]
        for field in ("compose", "compose-material3", "kotlin"):
            if field == "kotlin" and not u["kotlin"]["managed"]:
                continue
            cur, desired = u[field]["current"], u[field]["desired"]
            if cur != desired:
                rows.append((name, field, cur, desired))
    return rows


def render_summary(updates):
    lines = []
    for name in sorted(updates):
        u = updates[name]
        status = "UPDATE" if u["changed"] else "up to date"
        lines.append("[%s] (%s) %s" % (name, u["track"], status))
        for field in ("compose", "compose-material3", "kotlin"):
            if field == "kotlin" and not u["kotlin"]["managed"]:
                continue
            cur, desired = u[field]["current"], u[field]["desired"]
            arrow = "  %-18s %s" % (field, cur) if cur == desired else \
                    "  %-18s %s -> %s" % (field, cur, desired)
            lines.append(arrow)
        for flag in u["flags"]:
            lines.append("  ! " + flag)
    return "\n".join(lines)


def render_pr_body(updates):
    rows = _diff_rows(updates)
    lines = ["## Compose version tracking", "",
             "Automated check against Maven Central + JetBrains release notes.", ""]
    if rows:
        lines += ["| Channel | Field | Current | Proposed |", "|---|---|---|---|"]
        lines += ["| %s | `%s` | `%s` | `%s` |" % r for r in rows]
    else:
        lines.append("No version changes detected.")
    flags = [(name, f) for name in sorted(updates) for f in updates[name]["flags"]]
    if flags:
        lines += ["", "### Notes"]
        lines += ["- **%s**: %s" % nf for nf in flags]
    lines += ["", "If CI is green, merge this PR and then run the **Release** workflow."]
    return "\n".join(lines) + "\n"


# --- Network fetchers -----------------------------------------------------------

def fetch_maven_versions(group_path, artifact):
    url = "%s/%s/%s/maven-metadata.xml" % (MAVEN_BASE, group_path, artifact)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    xml = urllib.request.urlopen(req, timeout=TIMEOUT).read().decode()
    versions = re.findall(r"<version>([^<]+)</version>", xml)
    if not versions:
        raise RuntimeError("No versions found in %s" % url)
    return versions


def make_github_notes_fetcher():
    cache = {}

    def fetch(tag):
        if tag in cache:
            return cache[tag]
        headers = {"User-Agent": UA, "Accept": "application/vnd.github+json"}
        token = os.environ.get("GITHUB_TOKEN")
        if token:
            headers["Authorization"] = "Bearer " + token
        req = urllib.request.Request("%s/%s" % (GH_RELEASES, tag), headers=headers)
        try:
            data = json.loads(urllib.request.urlopen(req, timeout=TIMEOUT).read().decode())
            body = data.get("body") or ""
        except (urllib.error.URLError, OSError, ValueError):
            # Best-effort: a missing tag (404), rate limit (403/429), 5xx, timeout, DNS
            # failure, or malformed JSON must not abort the run — Kotlin is optional and
            # the caller keeps the current value and flags it. (HTTPError subclasses URLError;
            # socket.timeout subclasses OSError; JSON decode errors are ValueError.)
            body = None
        cache[tag] = body
        return body

    return fetch


# --- Self-test (offline) --------------------------------------------------------

def run_self_test():
    passed = [0]
    failed = []

    def check(name, cond):
        if cond:
            passed[0] += 1
        else:
            failed.append(name)
            print("FAIL - " + name)

    cv = ["1.10.0-alpha04", "1.10.0", "1.10.3", "1.11.0-alpha01", "1.11.0-beta02",
          "1.11.0-rc01", "1.11.0", "1.11.1", "1.12.0-alpha01"]
    mv = ["1.10.0-alpha04", "1.10.0-alpha05", "1.11.0-alpha06", "1.11.0-alpha07",
          "1.12.0-alpha01"]

    check("latest_stable", latest_stable(cv) == "1.11.1")
    check("latest_prerelease", latest_prerelease(cv) == "1.12.0-alpha01")
    check("order rc>beta>alpha",
          parse_version("1.11.0-rc01") > parse_version("1.11.0-beta02") > parse_version("1.11.0-alpha01"))
    check("order stable>rc", parse_version("1.11.0") > parse_version("1.11.0-rc01"))
    check("order patch>minor.0", parse_version("1.11.1") > parse_version("1.11.0"))
    check("order alpha10>alpha09", parse_version("1.11.0-alpha10") > parse_version("1.11.0-alpha09"))
    check("latest_prerelease ignores stable-only", latest_prerelease(["1.0.0", "1.1.0"]) is None)

    check("m3 match stable line", match_material3("1.11.1", mv) == "1.11.0-alpha07")
    check("m3 match alpha", match_material3("1.12.0-alpha01", mv) == "1.12.0-alpha01")
    check("m3 missing -> None", match_material3("1.20.0", mv) is None)

    check("kotlin 'is required'", parse_kotlin_from_notes("Kotlin 2.3.20 is required") == "2.3.20")
    check("kotlin 'based on'", parse_kotlin_from_notes("based on Kotlin 2.2.21") == "2.2.21")
    check("kotlin none", parse_kotlin_from_notes("Kotlin 2.3 line only, no patch") is None)
    check("kotlin empty", parse_kotlin_from_notes("") is None)
    # A bare/compatibility mention must NOT be parsed (would return the wrong toolchain).
    check("kotlin compat mention -> None",
          parse_kotlin_from_notes("Works with Kotlin 2.1.21 and newer. Built with Kotlin 2.2.20.") is None)
    check("kotlin 'requires at least' first-match avoided",
          parse_kotlin_from_notes("Requires at least Kotlin 2.0.0. Kotlin 2.3.20 is required for JS.") == "2.3.20")

    notes = {
        "v1.11.0": "Migrated. Kotlin 2.3.20 is required when using Compose with Kotlin/JS.",
        "v1.11.1": "Bug fixes only.",
    }
    fetch = lambda tag: notes.get(tag)
    k, tag = resolve_kotlin("1.11.1", fetch)
    check("kotlin falls back to GA notes", k == "2.3.20" and tag == "v1.11.0")
    k2, _ = resolve_kotlin("1.12.0-alpha01", fetch)
    check("kotlin unresolved -> None", k2 is None)

    channels = {
        "stable": {"compose": "1.10.3", "compose-material3": "1.10.0-alpha05"},
        "next": {"compose": "1.11.0-beta02", "compose-material3": "1.11.0-alpha06",
                 "kotlin": "2.3.20"},
    }
    ups = compute_updates(channels, cv, mv, fetch)
    check("stable compose bump", ups["stable"]["compose"]["desired"] == "1.11.1")
    check("stable m3 bump", ups["stable"]["compose-material3"]["desired"] == "1.11.0-alpha07")
    check("stable kotlin unmanaged", ups["stable"]["kotlin"]["managed"] is False)
    check("stable changed", ups["stable"]["changed"] is True)
    check("next compose bump", ups["next"]["compose"]["desired"] == "1.12.0-alpha01")
    check("next m3 bump", ups["next"]["compose-material3"]["desired"] == "1.12.0-alpha01")
    check("next kotlin kept when unresolved", ups["next"]["kotlin"]["desired"] == "2.3.20")
    check("next flags kotlin uncertainty", any("Kotlin" in f for f in ups["next"]["flags"]))

    # Guard: latest prerelease exists but is not newer than stable -> keep current 'next'.
    ups2 = compute_updates(channels, ["1.11.0", "1.11.1", "1.11.0-rc01"], mv, fetch)
    check("guard keeps next when prerelease not newer than stable",
          ups2["next"]["compose"]["desired"] == "1.11.0-beta02")
    check("guard flags the hold", any("No prerelease newer" in f for f in ups2["next"]["flags"]))

    # Explicit track key overrides name-based default.
    ups3 = compute_updates({"foo": {"compose": "1.0.0", "compose-material3": "1.0.0",
                                    "track": "stable"}}, cv, mv, fetch)
    check("track key forces stable", ups3["foo"]["compose"]["desired"] == "1.11.1")

    # No-op when already at latest.
    ups4 = compute_updates({"stable": {"compose": "1.11.1",
                                       "compose-material3": "1.11.0-alpha07"}}, cv, mv, fetch)
    check("no change when current==latest", ups4["stable"]["changed"] is False)

    # m3 missing -> keep current + flag, not crash.
    ups5 = compute_updates({"stable": {"compose": "1.10.3",
                                       "compose-material3": "1.10.0-alpha05"}},
                           ["1.20.0"], mv, fetch)
    check("m3 missing keeps current", ups5["stable"]["compose-material3"]["desired"] == "1.10.0-alpha05")

    # TOML rewrite round-trips and is section-scoped.
    sample = ('[stable]\ncompose = "1.10.3"\ncompose-material3 = "1.10.0-alpha05"\n\n'
              '[next]\ncompose = "1.11.0-beta02"\ncompose-material3 = "1.11.0-alpha06"\n'
              'kotlin = "2.3.20"\n')
    new, changed = rewrite_channel_key(sample, "stable", "compose", "1.11.1")
    check("rewrite sets changed", changed is True)
    check("rewrite applied", parse_toml_channels(new)["stable"]["compose"] == "1.11.1")
    check("rewrite leaves other section intact",
          parse_toml_channels(new)["next"]["compose"] == "1.11.0-beta02")
    check("rewrite does not touch compose-material3 when targeting compose",
          parse_toml_channels(new)["stable"]["compose-material3"] == "1.10.0-alpha05")
    _, noop = rewrite_channel_key(sample, "stable", "compose", "1.10.3")
    check("rewrite no-op flag", noop is False)
    scoped, _ = rewrite_channel_key(sample, "next", "kotlin", "2.4.0")
    check("rewrite section-scoped key", parse_toml_channels(scoped)["next"]["kotlin"] == "2.4.0")

    # apply_updates end-to-end against the sample TOML.
    new_text, any_changed = apply_updates(sample, compute_updates(
        parse_toml_channels(sample), cv, mv, fetch))
    rt = parse_toml_channels(new_text)
    check("apply_updates bumps stable compose", rt["stable"]["compose"] == "1.11.1")
    check("apply_updates bumps next compose", rt["next"]["compose"] == "1.12.0-alpha01")
    check("apply_updates reports change", any_changed is True)

    # Unknown / malformed versions sort lowest and never crash selection.
    check("unknown version excluded from stable", latest_stable(["garbage", "1.0.0"]) == "1.0.0")
    check("unknown version excluded from prerelease",
          latest_prerelease(["garbage", "1.1.0-alpha01"]) == "1.1.0-alpha01")

    # No-downgrade: latest upstream prerelease older than the current pin -> keep current.
    ups6 = compute_updates(
        {"next": {"compose": "1.11.0-beta02", "compose-material3": "1.11.0-alpha06",
                  "kotlin": "2.3.20"}},
        ["1.10.0", "1.11.0-alpha01"], mv, fetch)
    check("no-downgrade keeps current compose", ups6["next"]["compose"]["desired"] == "1.11.0-beta02")
    check("no-downgrade flagged", any("older than current" in f for f in ups6["next"]["flags"]))

    # Prerelease guard uses the global latest stable even with no stable-tracked channel.
    ups7 = compute_updates(
        {"next": {"compose": "1.9.0-beta01", "compose-material3": "1.9.0-alpha01",
                  "kotlin": "2.0.0"}},
        ["1.11.0", "1.11.1", "1.10.0-rc01"], mv, fetch)
    check("global max_stable guard holds without a stable channel",
          ups7["next"]["compose"]["desired"] == "1.9.0-beta01")

    # Invalid track value is rejected loudly.
    try:
        compute_updates({"x": {"compose": "1.0.0", "compose-material3": "1.0.0",
                               "track": "Stable"}}, cv, mv, fetch)
        check("invalid track raises", False)
    except ValueError:
        check("invalid track raises", True)

    # apply_updates raises (never silently drops) when a needed line cannot be matched.
    bad_text = '[s]\ncompose = "1.0.0" pinned\n'
    bad_ups = {"s": {"compose": {"current": "1.0.0", "desired": "2.0.0"},
                     "compose-material3": {"current": None, "desired": None},
                     "kotlin": {"current": None, "desired": None, "managed": False}}}
    try:
        apply_updates(bad_text, bad_ups)
        check("apply_updates raises on unmatched line", False)
    except RuntimeError:
        check("apply_updates raises on unmatched line", True)

    total = passed[0] + len(failed)
    if failed:
        print("\n%d/%d checks passed, %d FAILED" % (passed[0], total, len(failed)))
        return 1
    print("All %d checks passed." % total)
    return 0


# --- CLI ------------------------------------------------------------------------

def main(argv):
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="print drift; exit 3 if updates exist")
    parser.add_argument("--write", action="store_true", help="rewrite compose-releases.toml in place")
    parser.add_argument("--pr-body", metavar="PATH", help="write a markdown PR-body summary to PATH")
    parser.add_argument("--self-test", action="store_true", help="run offline tests (no network)")
    args = parser.parse_args(argv)

    if args.self_test:
        return run_self_test()

    with open(TOML_PATH, encoding="utf-8") as f:
        text = f.read()
    channels = parse_toml_channels(text)
    if not channels:
        print("No channels found in %s" % TOML_PATH, file=sys.stderr)
        return 1

    compose_versions = fetch_maven_versions("org/jetbrains/compose/runtime", "runtime")
    m3_versions = fetch_maven_versions("org/jetbrains/compose/material3", "material3")
    updates = compute_updates(channels, compose_versions, m3_versions, make_github_notes_fetcher())

    print(render_summary(updates))
    # Drift is decided by the same computation that the summary/PR body display, so the
    # exit code and the human-facing report can never disagree.
    drift = any(u["changed"] for u in updates.values())

    if args.write:
        if drift:
            new_text, _ = apply_updates(text, updates)  # raises if a change can't be written
            with open(TOML_PATH, "w", encoding="utf-8") as f:
                f.write(new_text)
            print("\nWrote updates to %s" % TOML_PATH)
        else:
            print("\nNo changes — %s left untouched." % TOML_PATH)

    if args.pr_body:
        with open(args.pr_body, "w", encoding="utf-8") as f:
            f.write(render_pr_body(updates))

    if args.check and drift:
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

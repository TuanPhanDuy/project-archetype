#!/usr/bin/env python3
"""Generates the Obsidian graph notes in docs/graph/ from .claude/, .github/workflows/ and sdlc.yaml."""
import argparse
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "graph"
GROUPS = ["agents", "skills", "commands", "workflows", "phases", "tiers", "ceremonies", "services"]
DOCS = {
    "ARCHITECTURE": "ARCHITECTURE.md",
    "TECH_RADAR": "docs/knowledge/TECH_RADAR.md",
    "PRD": "PRD.md",
    "GETTING_STARTED": "GETTING_STARTED.md",
    "CONTRIBUTING": "CONTRIBUTING.md",
    "MCP_SERVERS": "docs/MCP_SERVERS.md",
}


class Node:
    def __init__(self, group, name, title, source=None, description="", body=""):
        self.group, self.name, self.title = group, name, title
        self.source, self.description, self.body = source, description, body
        self.props = []   # "**Label:** value" lines
        self.links = {}   # label -> [node names]
        self.patterns = []

    def link(self, label, *names):
        targets = self.links.setdefault(label, [])
        targets.extend(n for n in names if n and n != self.name and n not in targets)


def split_frontmatter(path):
    text = path.read_text(encoding="utf-8")
    if text.startswith("---\n"):
        head, _, body = text[4:].partition("\n---\n")
        return yaml.safe_load(head) or {}, body.strip()
    return {}, text.strip()


def oneline(value):
    return " ".join(str(value or "").split())


def word(name):
    return re.compile(rf"(?<![\w-]){re.escape(name)}(?![\w-])")


def collect():
    sdlc = yaml.safe_load((ROOT / "sdlc.yaml").read_text(encoding="utf-8"))
    nodes = {}

    def add(node):
        nodes[node.name] = node
        return node

    roles = {r["agent"]: r for r in sdlc.get("roles", [])}
    defaults = sdlc.get("model_selection", {}).get("defaults_by_role", {})
    for path in sorted((ROOT / ".claude/agents").glob("*.md")):
        if path.name == "README.md":
            continue
        meta, body = split_frontmatter(path)
        node = add(Node("agents", path.stem, path.stem, path, oneline(meta.get("description")), body))
        if path.stem in roles:
            node.props.append(f"**Role:** {roles[path.stem]['title']} — {oneline(roles[path.stem]['skill'])}")
        if path.stem in defaults:
            node.props.append("**Model:** " + ", ".join(f"{k}: {v}" for k, v in defaults[path.stem].items()))
        if meta.get("tools"):
            node.props.append(f"**Tools:** {meta['tools']}")
        node.patterns.append(word(path.stem))

    for path in sorted((ROOT / ".claude/skills").glob("*/SKILL.md")):
        meta, body = split_frontmatter(path)
        name = path.parent.name
        node = add(Node("skills", f"skill-{name}", f"skill · {name}", path, oneline(meta.get("description")), body))
        node.patterns += [re.compile(rf"skills/{re.escape(name)}\b"), re.compile(rf"`/?{re.escape(name)}`")]
        if "-" in name:
            node.patterns.append(word(name))

    for path in sorted((ROOT / ".claude/commands").glob("*.md")):
        meta, body = split_frontmatter(path)
        node = add(Node("commands", f"cmd-{path.stem}", f"/{path.stem}", path, oneline(meta.get("description")), body))
        node.patterns += [re.compile(rf"(?<![\w.])/{re.escape(path.stem)}\b"), re.compile(rf"commands/{re.escape(path.stem)}\.md")]

    for path in sorted((ROOT / ".github/workflows").glob("*.yml")):
        text = path.read_text(encoding="utf-8")
        spec = yaml.safe_load(text) or {}
        triggers = spec.get("on", spec.get(True, {}))  # PyYAML reads a bare `on` key as True
        node = add(Node("workflows", f"wf-{path.stem}", f"workflow · {spec.get('name', path.stem)}", path, body=text))
        node.props.append("**Triggers:** " + ", ".join(sorted(triggers if isinstance(triggers, dict) else [str(triggers)])))
        node.props.append("**Jobs:** " + ", ".join(j.get("name", k) for k, j in spec.get("jobs", {}).items()))
        node.patterns.append(re.compile(rf"(?<![\w-]){re.escape(path.name)}"))

    phases = sdlc.get("phases", [])
    for i, phase in enumerate(phases):
        node = add(Node("phases", f"phase-{phase['id']}", f"phase · {phase['name']}"))
        node.link("Owner", phase.get("owner"))
        if phase.get("driven_by"):
            node.link("Driven by", phase["driven_by"].split(",")[0].strip())
        if i + 1 < len(phases):
            node.link("Next", f"phase-{phases[i + 1]['id']}")
        for key in ("inputs", "outputs"):
            if phase.get(key):
                node.props.append(f"**{key.title()}:** " + "; ".join(map(str, phase[key])))
        for key in ("entry_gate", "exit_gate"):
            if phase.get(key):
                node.props.append(f"**{key.replace('_', ' ').title()}:** {oneline(phase[key])}")

    profiles = sdlc.get("workflow_profiles", {})
    for tier in sdlc.get("task_classification", {}).get("tiers", []):
        node = add(Node("tiers", f"tier-{tier['id']}", f"tier · {tier['name']}", description=oneline(tier.get("criteria"))))
        node.link("Runs phases", *(f"phase-{p}" for p in profiles.get(tier["id"], {}).get("phases", [])))
        node.patterns.append(re.compile(rf"`{re.escape(tier['id'])}`" if "_" not in tier["id"] else rf"\b{tier['id']}\b"))

    for ceremony in sdlc.get("ceremonies", []):
        node = add(Node("ceremonies", f"ceremony-{ceremony['id']}", f"ceremony · {ceremony['name']}", description=oneline(ceremony.get("purpose"))))
        node.props.append(f"**Cadence:** {ceremony.get('cadence', '')}")
        node.link("Owner", ceremony.get("owner"))
        node.link("Participants", *ceremony.get("participants", []))
        if ceremony.get("command"):
            node.link("Command", f"cmd-{ceremony['command'].lstrip('/')}")

    # The current service gets a fixed file name so renaming the service doesn't move the note.
    registry = sdlc.get("cross_service", {}).get("registry", [])
    svc_file = {s["name"]: "svc-this-service" if not s.get("repo") else f"svc-{s['name']}" for s in registry}
    for service in registry:
        node = add(Node("services", svc_file[service["name"]], f"service · {service['name']}"))
        node.props.append(f"**Repo:** {service.get('repo') or 'this repo'}")
        node.props.append("**Owns:** " + (", ".join(service.get("owns", [])) or "—"))
        node.link("Consumes", *(svc_file.get(c, f"svc-{c}") for c in service.get("consumes", [])))

    for node in nodes.values():
        text = f"{node.description}\n{node.body}"
        if not text.strip():
            continue
        node.link("Mentions", *sorted(o.name for o in nodes.values() if any(p.search(text) for p in o.patterns)))
        node.link("Docs", *sorted(d for d, rel in DOCS.items() if Path(rel).name in text and (ROOT / rel).exists()))
    return nodes


def render(node):
    lines = ["---", f"tags: [{node.group[:-1]}]", "generated: true", "---", f"# {node.title}", ""]
    if node.description:
        lines += [f"> {node.description}", ""]
    lines += [f"- {p}" for p in node.props]
    lines += [f"- **{label}:** " + ", ".join(f"[[{n}]]" for n in names) for label, names in node.links.items() if names]
    if node.source:
        lines.append(f"- **Source:** `{node.source.relative_to(ROOT)}`")
    # Workflow text is left out so action version bumps don't make the graph stale.
    if node.body and node.group != "workflows":
        lines += ["", "## Source text", "", node.body]
    return "\n".join(lines).rstrip() + "\n"


def render_index(nodes):
    lines = [
        "---", "tags: [index]", "generated: true", "---", "# SDLC graph", "",
        "Generated by `docs/graph/build-graph.py` — edit the sources, not these notes.",
        "Open the repo root as an Obsidian vault and use Graph view.", "",
    ]
    for group in GROUPS:
        members = sorted(n.name for n in nodes.values() if n.group == group)
        if members:
            lines += [f"## {group.title()}", ", ".join(f"[[{m}]]" for m in members), ""]
    docs = [d for d, rel in DOCS.items() if (ROOT / rel).exists()]
    lines += ["## Docs", ", ".join(f"[[{d}]]" for d in docs)]
    adrs = sorted(p.stem for p in (ROOT / "docs/adr").glob("*.md"))
    if adrs:
        lines += ["", "## ADRs", ", ".join(f"[[{a}]]" for a in adrs)]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if docs/graph is out of date")
    args = parser.parse_args()

    nodes = collect()
    wanted = {OUT / n.group / f"{n.name}.md": render(n) for n in nodes.values()}
    wanted[OUT / "Index.md"] = render_index(nodes)
    existing = {p for g in GROUPS for p in (OUT / g).glob("*.md")} | {OUT / "Index.md"}

    stale = sorted(p for p in existing - wanted.keys() if p.exists())
    changed = sorted(p for p, text in wanted.items() if not p.exists() or p.read_text(encoding="utf-8") != text)
    if args.check:
        for p in changed + stale:
            print(f"out of date: {p.relative_to(ROOT)}")
        if changed or stale:
            print("Run: python3 docs/graph/build-graph.py")
            return 1
        return 0
    for p in stale:
        p.unlink()
    for p in changed:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(wanted[p], encoding="utf-8")
    print(f"{len(changed)} written, {len(stale)} removed, {len(wanted)} notes")
    return 0


if __name__ == "__main__":
    sys.exit(main())

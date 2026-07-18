# scripts

## `new-project.sh` — generate a new service from this archetype

Copies the archetype into a new directory and renames everything (base package, Maven
`groupId`/`artifactId`, and `spring.application.name`) across sources, tests, config, the
k6 suite, the observability stack, and docs. The result is a self-contained, buildable
project — verified by generating one and running its full `./mvnw verify`.

### Usage

Run it with no arguments to be prompted for each value (name and groupId are required;
package and output directory have sensible defaults you can accept with Enter):

```bash
scripts/new-project.sh
# Project name (artifactId): order-service
# Maven groupId: com.acme
# Base package [com.acme.orderservice]:
# Output directory [../order-service]:
# Proceed? [Y/n]:
```

Or pass everything as flags (required for non-interactive/CI use — with no TTY and missing
flags it exits with an error rather than hanging on a prompt):

```bash
scripts/new-project.sh --name <artifact> --group <groupId> [--package <base.pkg>] [--output <dir>]
```

| Flag              | Required | Default                          | Meaning                              |
|-------------------|----------|----------------------------------|--------------------------------------|
| `-n, --name`      | yes      | —                                | artifactId + app name (`order-service`) |
| `-g, --group`     | yes      | —                                | Maven groupId (`com.acme`)           |
| `-p, --package`   | no       | `<group>.<name without dashes>`  | Java base package                    |
| `-o, --output`    | no       | `../<name>`                      | target directory                     |
| `--force`         | no       | off                              | overwrite an existing target         |
| `--no-git`        | no       | off                              | skip `git init` in the new project   |

### Example

```bash
scripts/new-project.sh \
  --name order-service \
  --group com.acme \
  --package com.acme.orderservice \
  --output ~/code/order-service

cd ~/code/order-service
./mvnw verify          # green: 4 unit + 7 integration tests (needs Docker)
```

### What it does

1. `rsync` the archetype, excluding `target/`, `.git/`, `scripts/`, and IDE files.
2. Move `src/{main,test}/java/com/onemount/archetype` → your package path.
3. Rewrite identifiers in every text file, most-specific first:
   `com.onemount.archetype` → your package, `com/onemount/archetype` → your path,
   `com.onemount` → your group, `service-archetype` → your artifact.
4. `git init` + stage (unless `--no-git`).

It does **not** strip the sample features (`product`, `category`, `order`, `job`) — they're
the reference implementation. Delete the ones you don't need after generating.

### Alternative: the Maven archetype

If you prefer `mvn archetype:generate`, this repo also ships an installable Maven archetype
in [`archetype/`](../archetype/) (built from this same project, so the two never drift):

```bash
./archetype/sync.sh && mvn -f archetype/pom.xml install
mvn archetype:generate -DarchetypeGroupId=com.onemount \
  -DarchetypeArtifactId=service-archetype -DarchetypeVersion=0.1.0
```

Both paths are verified the same way (generate → `./mvnw verify` green). Use the script for
zero setup; use the archetype for IDE integration or publishing to your Maven registry.

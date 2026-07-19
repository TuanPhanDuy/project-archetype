# Maven archetype

Generates a new service from this reference project with the native Maven command:

```bash
mvn archetype:generate \
  -DarchetypeGroupId=com.anbit \
  -DarchetypeArtifactId=service-archetype \
  -DarchetypeVersion=0.1.0
```

You'll be prompted for the new project's `groupId`, `artifactId`, `version`, `package`, and
`purpose` (a one-line description of what the service is for, e.g. "an order management API
for e-commerce checkout" — threaded into the copied `.claude/` agent/skill/command system
prompts, same as `scripts/new-project.sh --purpose`; defaults to "this service" if left
blank). Or pass everything with
`-DgroupId=… -DartifactId=… -Dpackage=… -Dpurpose=… -DinteractiveMode=false`. The
result is a self-contained project that builds green out of the box (`./mvnw verify`).

> Prefer no Maven setup? `scripts/new-project.sh` does the same thing as a shell command.
> Both are kept in sync because the archetype is built *from* the real project.

## Build & install it locally

The archetype's resources are generated from the project, so sync first:

```bash
./archetype/sync.sh                              # copy the project into archetype-resources/
mvn -f archetype/pom.xml clean install           # install to your local ~/.m2
```

(Use JDK 25 for the build, e.g. `JAVA_HOME=$(/usr/libexec/java_home -v 25)`.)

## Publish it (so teammates don't need this repo)

```bash
./archetype/sync.sh
mvn -f archetype/pom.xml clean deploy            # to your Nexus/Artifactory (configure distributionManagement)
```

Then anyone can `mvn archetype:generate -DarchetypeGroupId=com.anbit -DarchetypeArtifactId=service-archetype -DarchetypeVersion=…`. A CI job can run `sync.sh` + `deploy` on tags.

## How it works (and why it's built this way)

A normal Maven archetype runs every file through **Velocity**, which would corrupt this
project: `${DB_URL:…}` in YAML, `${{ github.* }}` in workflows, `${BASE_URL}` in k6, and
`##` headings in Markdown all collide with Velocity syntax. So instead:

- **Everything is copied raw** (`filtered="false"` in `archetype-metadata.xml`) — no Velocity
  templating of file contents.
- **`META-INF/archetype-post-generate.groovy`** then does the renaming (package → your
  package, `com.anbit` → your groupId, `service-archetype` → your artifactId,
  `0.1.0-SNAPSHOT` → your version) and moves the Java sources into your package — the same
  transformation as `scripts/new-project.sh`.
- The only file Maven force-filters is the root `pom.xml`; `sync.sh` wraps its
  `${org.mockito:mockito-core:jar}` token in a Velocity literal block so it survives.

## Files

| Path | Purpose |
|------|---------|
| `pom.xml` | archetype module (`maven-archetype` packaging) |
| `sync.sh` | copies the project into `src/main/resources/archetype-resources/` (gitignored) |
| `src/main/resources/META-INF/maven/archetype-metadata.xml` | filesets (all `filtered=false`) |
| `src/main/resources/META-INF/archetype-post-generate.groovy` | post-generation renaming |

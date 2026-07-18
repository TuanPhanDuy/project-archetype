// Runs after the archetype copies files. Renames the reference project's identifiers to the
// user's chosen coordinates — the same transformation as scripts/new-project.sh — and moves
// the Java sources into the chosen package. Everything was copied raw, so no Velocity damage.

import java.nio.file.Files
import java.nio.file.StandardCopyOption

def artifactId = request.artifactId
def groupId    = request.groupId
def version    = request.version
def pkg        = request.package
def root       = new File(request.outputDirectory, artifactId)

final OLD_PKG     = 'com.onemount.archetype'
final OLD_PKGPATH = 'com/onemount/archetype'
final OLD_GROUP   = 'com.onemount'
final OLD_NAME    = 'service-archetype'
final OLD_VERSION = '0.1.0-SNAPSHOT'
def pkgPath = pkg.replace('.', '/')

// 1) Move Java sources from the old package path to the chosen package.
['src/main/java', 'src/test/java'].each { base ->
    def oldDir = new File(root, "$base/$OLD_PKGPATH")
    if (oldDir.directory) {
        def newDir = new File(root, "$base/$pkgPath")
        newDir.parentFile.mkdirs()
        Files.move(oldDir.toPath(), newDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}

// 2) Prune now-empty leftover package directories (e.g. com/onemount).
def pruneEmpty
pruneEmpty = { File d ->
    if (d == null || !d.directory) return
    d.listFiles().each { if (it.directory) pruneEmpty(it) }
    def kids = d.listFiles()
    if (kids != null && kids.length == 0) d.delete()
}
['src/main/java', 'src/test/java'].each { pruneEmpty(new File(root, it)) }

// 3) Rewrite identifiers in every text file (most-specific first).
root.eachFileRecurse { f ->
    if (!f.file) return
    def text
    try {
        text = f.getText('UTF-8')
    } catch (ignored) {
        return // skip anything that isn't UTF-8 text
    }
    def out = text
            .replace(OLD_PKG, pkg)
            .replace(OLD_PKGPATH, pkgPath)
            .replace(OLD_GROUP, groupId)
            .replace(OLD_NAME, artifactId)
            .replace(OLD_VERSION, version)
    if (out != text) f.setText(out, 'UTF-8')
}

// 4) Restore the Maven wrapper's executable bit (lost when copied through the archetype).
new File(root, 'mvnw').setExecutable(true, false)

println "Generated ${artifactId} (package ${pkg}) from service-archetype."

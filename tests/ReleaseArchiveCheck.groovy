import groovy.json.JsonOutput
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

def targetRoot = new File(args[0]).canonicalFile
def archiveClass = new GroovyClassLoader(getClass().classLoader).parseClass(new File(targetRoot, 'gradle/ReleaseArchive.groovy'))
def parent = new File(targetRoot, 'build/release-archive-check').toPath()
Files.createDirectories(parent)
def fixture = Files.createTempDirectory(parent, 'fixture-')
int assertions = 0
def verify = { boolean condition, String message -> assertions++; assert condition : message }
def expectFailure = { Closure operation ->
    try { operation(); assert false : 'Invalid publication must fail' }
    catch (IOException | IllegalArgumentException expected) { assertions++ }
}
def version = '1.1.1-1.21.1-beta.6'
def writeJar = { Path path, String loader, String v = version, String marker = 'checked', String id = 'mtrsteamloco', String name = 'YLTE — Yanling Transit Expansion' ->
    new ZipOutputStream(Files.newOutputStream(path)).withCloseable { jar ->
        jar.putNextEntry(new ZipEntry(loader == 'fabric' ? 'fabric.mod.json' : 'META-INF/neoforge.mods.toml'))
        def metadata = loader == 'fabric' ? JsonOutput.toJson([id: id, name: name, version: v])
                : "[[mods]]\nmodId = \"${id}\"\nversion = \"${v}\"\ndisplayName = \"${name}\"\n[[dependencies.mtrsteamloco]]\nmodId = \"minecraft\"\n"
        jar.write(metadata.getBytes('UTF-8')); jar.closeEntry()
        jar.putNextEntry(new ZipEntry('marker.txt')); jar.write(marker.getBytes('UTF-8')); jar.closeEntry()
    }
    path.toFile()
}
['fabric', 'neoforge'].each { loader ->
    def release = Files.createDirectory(fixture.resolve(loader))
    def candidate = writeJar(fixture.resolve("${loader}.jar"), loader)
    def current = release.resolve("YLTE-${loader}-${version}.jar")
    def oldNames = ["MTR-ANTE-${loader}-1.1.1-1.21.1-beta.5.jar", "YLTE-${loader}-1.1.1-1.21.1-beta.4.jar"]
    def otherLoader = loader == 'fabric' ? 'neoforge' : 'fabric'
    def retained = ["MTR-ANTE-${otherLoader}-1.1.1-1.21.1-beta.5.jar", "YLTE-${loader}-1.1.1-1.21.10-beta.5.jar", "YLTE-${loader}-1.1.1-26.2-beta.5.jar", "YLTE-server-${loader}-${version}.jar", "YanlingMTR-${loader}-1.21.1-1.0.0.jar", 'notes.txt']
    (oldNames + retained).each { Files.writeString(release.resolve(it), it) }
    def archived = archiveClass.publish(candidate, release.toFile(), loader, version)
    verify(archived.size() == 2, 'Only this game, loader and release family may be archived')
    oldNames.each { name -> verify(!Files.exists(release.resolve(name)) && archived.any { it.fileName.toString() == name && Files.readString(it) == name }, 'Old contents must remain recoverable') }
    retained.each { verify(Files.readString(release.resolve(it)) == it, 'Unrelated artifact changed') }
    verify(Files.mismatch(current, candidate.toPath()) == -1, 'Publication changed checked bytes')
    def timestamp = Files.getLastModifiedTime(current)
    verify(archiveClass.publish(candidate, release.toFile(), loader, version).empty, 'Identical publication must not archive again')
    verify(Files.getLastModifiedTime(current) == timestamp, 'Identical publication changed the file')
    def rebuilt = writeJar(fixture.resolve("${loader}-rebuilt.jar"), loader, version, 'rebuilt')
    def replaced = archiveClass.publish(rebuilt, release.toFile(), loader, version)
    verify(replaced.size() == 1 && Files.mismatch(replaced[0], candidate.toPath()) == -1, 'Same-name old bytes lost')
    verify(Files.mismatch(current, rebuilt.toPath()) == -1, 'New bytes not promoted')
    Files.writeString(release.resolve(oldNames[0]), 'later history')
    def later = archiveClass.publish(rebuilt, release.toFile(), loader, version)
    verify(later.size() == 1 && Files.readString(later[0]) == 'later history', 'Later history lost')
    verify(Files.readString(archived[0]) == archived[0].fileName.toString(), 'Earlier history overwritten')
    def badCandidates = [fixture.resolve('missing.jar').toFile(), Files.writeString(fixture.resolve("${loader}-broken.jar"), 'broken').toFile(),
        writeJar(fixture.resolve("${loader}-wrong-version.jar"), loader, 'old'), writeJar(fixture.resolve("${loader}-wrong-loader.jar"), otherLoader),
        writeJar(fixture.resolve("${loader}-wrong-id.jar"), loader, version, 'bad', 'mtr'), writeJar(fixture.resolve("${loader}-wrong-name.jar"), loader, version, 'bad', 'mtrsteamloco', 'ANTE')]
    badCandidates.each { bad ->
        expectFailure { archiveClass.publish(bad, release.toFile(), loader, version) }
        verify(Files.mismatch(current, rebuilt.toPath()) == -1, 'Invalid candidate replaced current')
    }
    def blocked = Files.createDirectory(fixture.resolve("${loader}-blocked"))
    Files.writeString(blocked.resolve('archive'), 'blocked')
    Files.writeString(blocked.resolve(oldNames[0]), 'previous')
    expectFailure { archiveClass.publish(candidate, blocked.toFile(), loader, version) }
    verify(Files.readString(blocked.resolve(oldNames[0])) == 'previous' && !Files.exists(blocked.resolve(current.fileName)), 'Blocked archive changed releases')
    def nonRegular = Files.createDirectory(fixture.resolve("${loader}-nonregular"))
    Files.createDirectory(nonRegular.resolve(oldNames[0]))
    expectFailure { archiveClass.publish(candidate, nonRegular.toFile(), loader, version) }
    verify(!Files.exists(nonRegular.resolve(current.fileName)), 'Non-regular previous file must block promotion')
    expectFailure { archiveClass.publish(candidate, release.toFile(), 'forge', version) }
    expectFailure { archiveClass.publish(candidate, release.toFile(), loader, '../escape') }
}
Files.walk(fixture).withCloseable { paths -> verify(paths.noneMatch { it.fileName.toString().startsWith('.publish-') }, 'Leaked staging files') }
println "PASS: ${assertions} YLTE recoverable release publication assertions"

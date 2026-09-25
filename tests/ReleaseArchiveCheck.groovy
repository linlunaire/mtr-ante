import java.nio.file.Files

def targetRoot = new File(args[0]).canonicalFile
def archiveClass = new GroovyClassLoader(getClass().classLoader)
        .parseClass(new File(targetRoot, 'gradle/ReleaseArchive.groovy'))
def fixtureParent = new File(targetRoot, 'build/release-archive-check').toPath()
Files.createDirectories(fixtureParent)
def fixture = Files.createTempDirectory(fixtureParent, 'fixture-')
def release = Files.createDirectory(fixture.resolve('release'))
def oldName = 'MTR-ANTE-neoforge-1.1.1-26.2-beta.2.jar'
def currentName = 'MTR-ANTE-neoforge-1.1.1-26.2.jar'
def retained = [
        currentName,
        'MTR-ANTE-fabric-1.1.1-26.2-beta.2.jar',
        'MTR-ANTE-neoforge-1.1.1-1.21.1-beta.2.jar',
        'MTR-ANTE-neoforge-1.1.1-26.20-beta.2.jar',
        'MTR-neoforge-26.2-3.3.2.jar',
        'notes.txt'
]
([oldName] + retained).each { Files.writeString(release.resolve(it), it) }
def archived = archiveClass.archivePrevious(release.toFile(), 'neoforge', currentName)
assert !Files.exists(release.resolve(oldName)) : 'A successful release must remove the obsolete same-loader 26.2 JAR from the top level'
assert archived.size() == 1
assert archived[0].startsWith(release.resolve('archive'))
assert Files.readString(archived[0]) == oldName : 'The obsolete JAR must remain recoverable'
retained.each { assert Files.readString(release.resolve(it)) == it : "Unexpectedly changed ${it}" }
assert archiveClass.archivePrevious(release.toFile(), 'neoforge', currentName).empty : 'A repeated release must be idempotent'

// Missing/failed replacement: even a directly invoked archive step must preserve the old JAR.
def missing = Files.createDirectory(fixture.resolve('missing-replacement'))
Files.writeString(missing.resolve(oldName), 'old')
try {
    archiveClass.archivePrevious(missing.toFile(), 'neoforge', currentName)
    assert false : 'Missing replacement must fail closed'
} catch (IllegalArgumentException expected) { }
assert Files.readString(missing.resolve(oldName)) == 'old'
Files.createFile(missing.resolve(currentName))
try {
    archiveClass.archivePrevious(missing.toFile(), 'neoforge', currentName)
    assert false : 'Empty replacement must fail closed'
} catch (IllegalArgumentException expected) { }
assert Files.readString(missing.resolve(oldName)) == 'old'

// Archive-path failure must not discard the old file or overwrite the obstruction.
def blocked = Files.createDirectory(fixture.resolve('blocked-archive'))
[oldName, currentName].each { Files.writeString(blocked.resolve(it), it) }
Files.writeString(blocked.resolve('archive'), 'unrelated')
try {
    archiveClass.archivePrevious(blocked.toFile(), 'neoforge', currentName)
    assert false : 'An unusable archive path must fail closed'
} catch (IOException expected) { }
assert Files.readString(blocked.resolve(oldName)) == oldName
assert Files.readString(blocked.resolve('archive')) == 'unrelated'

// Existing archives are preserved, including an identically named historical file.
Files.writeString(release.resolve(oldName), 'second old build')
def archivedAgain = archiveClass.archivePrevious(release.toFile(), 'neoforge', currentName)
assert archivedAgain.size() == 1 && archivedAgain[0] != archived[0]
assert Files.readString(archivedAgain[0]) == 'second old build'
assert Files.readString(archived[0]) == oldName
println 'PASS: release archive scope, recovery, repeated builds and failure paths'

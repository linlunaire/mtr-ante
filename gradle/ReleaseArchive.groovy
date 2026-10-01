import groovy.json.JsonSlurper
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern
import java.util.zip.ZipFile

/** Publishes a checked 1.21.1 JAR before archiving only its own release family. */
final class ReleaseArchive {
    static List<Path> publish(File source, File directory, String loader, String version) {
        if (!(loader in ['fabric', 'neoforge']) || !(version ==~ /[A-Za-z0-9.+]+-1\.21\.1-[A-Za-z0-9.+-]+/)) {
            throw new IllegalArgumentException('Expected a supported YLTE 1.21.1 release version')
        }
        validateArtifact(source, loader, version)
        Path root = directory.toPath().toAbsolutePath().normalize()
        Files.createDirectories(root)
        root = root.toRealPath()
        Path current = root.resolve("YLTE-${loader}-${version}.jar")
        def names = Pattern.compile('^(?:MTR-ANTE|YLTE)-' + Pattern.quote(loader) + '-[^/]+-1\\.21\\.1-.+\\.jar$')
        List<Path> previous
        Files.list(root).withCloseable { entries ->
            previous = entries.filter { it != current && names.matcher(it.fileName.toString()).matches() }
                    .sorted().toList()
        }
        // Validate every destination/source before replacing or moving anything.
        (previous + (Files.exists(current, LinkOption.NOFOLLOW_LINKS) ? [current] : [])).each {
            if (!Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) || it.toRealPath().parent != root) {
                throw new IOException("Refusing a non-local regular release JAR: ${it}")
            }
        }
        Path archive = root.resolve('archive')
        if (Files.exists(archive, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(archive, LinkOption.NOFOLLOW_LINKS) || archive.toRealPath() != archive)) {
            throw new IOException("Refusing an archive path outside the release directory: ${archive}")
        }
        List<Path> archived = []
        Path batch = null
        Path staged = Files.createTempFile(root, '.publish-', '.jar')
        try {
            Files.copy(source.toPath(), staged, StandardCopyOption.REPLACE_EXISTING)
            validateArtifact(staged.toFile(), loader, version)
            if (Files.mismatch(source.toPath(), staged) != -1) throw new IOException('Release source changed during publication')
            if (!Files.exists(current) || Files.mismatch(staged, current) != -1) {
                if (Files.exists(current)) {
                    Files.createDirectories(archive)
                    batch = Files.createTempDirectory(archive, 'previous-')
                    archived.add(Files.copy(current, batch.resolve(current.fileName)))
                }
                try {
                    Files.move(staged, current, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(staged, current, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            // A failed candidate copy or promotion never removes an older release.
            if (!previous.empty) {
                Files.createDirectories(archive)
                if (batch == null) batch = Files.createTempDirectory(archive, 'previous-')
                previous.each { archived.add(Files.move(it, batch.resolve(it.fileName))) }
            }
            return archived
        } finally {
            Files.deleteIfExists(staged)
        }
    }

    static void validateArtifact(File artifact, String loader, String version) {
        if (!Files.isRegularFile(artifact.toPath(), LinkOption.NOFOLLOW_LINKS) || artifact.length() == 0) {
            throw new IOException("Missing or empty release JAR: ${artifact}")
        }
        new ZipFile(artifact).withCloseable { jar ->
            def entry = jar.getEntry(loader == 'fabric' ? 'fabric.mod.json' : 'META-INF/neoforge.mods.toml')
            if (entry == null) throw new IOException("Missing ${loader} release metadata: ${artifact}")
            String metadata = jar.getInputStream(entry).withCloseable { it.getText('UTF-8') }
            boolean matches
            if (loader == 'fabric') {
                def mod = new JsonSlurper().parseText(metadata)
                matches = mod.id == 'mtrsteamloco' && mod.name == 'YLTE — Yanling Transit Expansion' && mod.version == version
            } else {
                matches = metadata.split(/\[\[mods\]\]/).drop(1).any { section ->
                    def mod = section.split(/(?m)^\s*\[/, 2)[0]
                    (mod =~ /(?m)^\s*modId\s*=\s*"mtrsteamloco"\s*$/).find()
                            && (mod =~ /(?m)^\s*displayName\s*=\s*"YLTE — Yanling Transit Expansion"\s*$/).find()
                            && (mod =~ ('(?m)^\\s*version\\s*=\\s*"' + Pattern.quote(version) + '"\\s*$')).find()
                }
            }
            if (!matches) throw new IOException("Release metadata does not match YLTE ${version}: ${artifact}")
        }
    }
}

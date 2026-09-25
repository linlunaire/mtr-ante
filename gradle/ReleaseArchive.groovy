import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.LinkOption
import java.util.regex.Pattern

/** Runs only after the checked replacement JAR has been copied successfully. */
final class ReleaseArchive {
    static List<Path> archivePrevious(File directory, String loader, String currentName) {
        if (!(loader in ['fabric', 'neoforge'])) throw new IllegalArgumentException("Unknown loader: ${loader}")
        def names = Pattern.compile('^MTR-ANTE-' + Pattern.quote(loader) + '-.+-26\\.2(?:[-+].+)?\\.jar$')
        if (!names.matcher(currentName).matches()) throw new IllegalArgumentException("Not a ${loader} 26.2 release: ${currentName}")
        Path root = directory.toPath().toRealPath()
        Path current = root.resolve(currentName).normalize()
        if (current.parent != root || !Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)
                || Files.size(current) == 0) {
            throw new IllegalArgumentException("Checked replacement JAR is missing or empty: ${current}")
        }
        List<Path> obsolete
        Files.list(root).withCloseable { entries ->
            obsolete = entries.filter { it.fileName.toString() != currentName && names.matcher(it.fileName.toString()).matches() }
                    .sorted().toList()
        }
        // Validate every source before moving anything; do not follow symlinks or junctions.
        obsolete.each {
            if (!Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) || it.toRealPath().parent != root) {
                throw new IOException("Refusing to archive a non-local regular JAR: ${it}")
            }
        }
        if (obsolete.empty) return []
        Path archive = root.resolve('archive')
        Files.createDirectories(archive)
        if (!Files.isDirectory(archive, LinkOption.NOFOLLOW_LINKS) || archive.toRealPath() != archive) {
            throw new IOException("Refusing an archive directory outside the release directory: ${archive}")
        }
        Path batch = Files.createTempDirectory(archive, 'previous-')
        List<Path> archived = []
        obsolete.each { previous ->
            Path destination = batch.resolve(previous.fileName).normalize()
            if (!destination.startsWith(archive) || previous.parent != root) {
                throw new IOException("Archive path escaped the release directory: ${previous}")
            }
            archived.add(Files.move(previous, destination))
        }
        return archived
    }
}

package com.github.fge.filesystem;

import com.github.fge.filesystem.copy.FailFastCopyVisitor;
import com.github.fge.filesystem.copy.KeepGoingCopyVisitor;
import com.github.fge.filesystem.deletion.FailFastDeletionVisitor;
import com.github.fge.filesystem.deletion.KeepGoingDeletionVisitor;
import com.github.fge.filesystem.exceptions.RecursiveCopyException;
import com.github.fge.filesystem.exceptions.RecursiveDeletionException;
import com.github.fge.filesystem.posix.PosixModes;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Utility classes in complement of the JDK's {@link Files}
 *
 * <p>Unless otherwise noted, all methods in this class do not accept null
 * arguments and will throw a {@link NullPointerException} if a null argument
 * is passed to them.</p>
 */
@ParametersAreNonnullByDefault
public final class MoreFiles
{
    private MoreFiles()
    {
        throw new Error("nice try!");
    }

    public static void copyRecursive(final Path source, final Path destination,
        final RecursionMode mode, final CopyOption... options)
        throws IOException
    {
        Objects.requireNonNull(mode);

        boolean replace = false;

        for (final CopyOption option: options) {
            Objects.requireNonNull(option);
            if (option == StandardCopyOption.REPLACE_EXISTING)
                replace = true;
        }

        // We only support one option; array must be empty or have one element
        if (options.length > 1)
            throw new UnsupportedOperationException();

        // This will throw NoSuchFileException for us if source does not exist
        final Path src = Objects.requireNonNull(source).toRealPath();
        final Path dst = Objects.requireNonNull(destination).toAbsolutePath();

        if (Files.exists(dst, LinkOption.NOFOLLOW_LINKS) && !replace)
            throw new FileAlreadyExistsException(destination.toString());

        Files.deleteIfExists(dst);

        if (mode == RecursionMode.FAIL_FAST) {
            Files.walkFileTree(src, new FailFastCopyVisitor(src, dst));
            return;
        }

        // Cannot happen in theory, but...
        if (mode != RecursionMode.KEEP_GOING)
            throw new IllegalStateException();

        final RecursiveCopyException e = new RecursiveCopyException();
        final FileVisitor<Path> visitor
            = new KeepGoingCopyVisitor(src, dst, e);

        Files.walkFileTree(src, visitor);
        if (e.getSuppressed().length != 0)
            throw e;
    }


    public static void deleteRecursive(final Path victim,
        final RecursionMode option)
        throws IOException
    {
        Objects.requireNonNull(victim);
        Objects.requireNonNull(option);

        final FileVisitor<Path> visitor;

        switch (option) {
            case KEEP_GOING:
                final RecursiveDeletionException exception
                    = new RecursiveDeletionException();
                visitor = new KeepGoingDeletionVisitor(victim, exception);
                Files.walkFileTree(victim, visitor);
                if (exception.getSuppressed().length != 0)
                    throw exception;
                break;
            case FAIL_FAST:
                visitor = new FailFastDeletionVisitor(victim);
                Files.walkFileTree(victim, visitor);
                break;
            default:
                throw new IllegalStateException();
        }
    }

    @Nonnull
    public static Path setMode(final int mode, final Path path)
        throws IOException
    {
        Objects.requireNonNull(path);
        final Set<PosixFilePermission> perms = PosixModes.intModeToPosix(mode);

        return Files.setPosixFilePermissions(path, perms);
    }

    @Nonnull
    public static Path setMode(final String modeString, final Path path)
        throws IOException
    {
        Objects.requireNonNull(modeString);
        Objects.requireNonNull(path);

        final Set<PosixFilePermission> perms
            = PosixFilePermissions.fromString(modeString);

        return Files.setPosixFilePermissions(path, perms);
    }

    @Nonnull
    public static Path createFile(final Path path,
        final String posixPermissions)
        throws IOException
    {
        Objects.requireNonNull(path);
        Objects.requireNonNull(posixPermissions);

        final Set<PosixFilePermission> perms
            = PosixFilePermissions.fromString(posixPermissions);

        return doCreateFile(path, perms);
    }

    @Nonnull
    public static Path createFile(final Path path, final int mode)
        throws IOException
    {
        Objects.requireNonNull(path);

        final Set<PosixFilePermission> perms = PosixModes.intModeToPosix(mode);

        return doCreateFile(path, perms);
    }

    @Nonnull
    public static Path createDirectory(final Path dir,
        final String posixPermissions)
        throws IOException
    {
        Objects.requireNonNull(dir);
        Objects.requireNonNull(posixPermissions);

        final Set<PosixFilePermission> perms
            = PosixFilePermissions.fromString(posixPermissions);

        return doCreateDirectory(dir, perms);
    }

    @Nonnull
    public static Path createDirectory(final Path dir, final int mode)
        throws IOException
    {
        Objects.requireNonNull(dir);

        final Set<PosixFilePermission> perms = PosixModes.intModeToPosix(mode);

        return doCreateDirectory(dir, perms);
    }

    @Nonnull
    public static Path createDirectories(final Path dir,
        final String posixPermissions)
        throws IOException
    {
        Objects.requireNonNull(dir);
        Objects.requireNonNull(posixPermissions);

        final Path realDir = dir.toAbsolutePath();
        final Set<PosixFilePermission> perms
            = PosixFilePermissions.fromString(posixPermissions);

        doCreateDirectories(realDir, perms);

        return dir;
    }

    @Nonnull
    public static Path createDirectories(final Path dir, final int mode)
        throws IOException
    {
        Objects.requireNonNull(dir);

        final Path realDir = dir.toAbsolutePath();
        final Set<PosixFilePermission> perms = PosixModes.intModeToPosix(mode);

        doCreateDirectories(realDir, perms);

        return dir;
    }

    private static Path doCreateFile(final Path path,
        final Set<PosixFilePermission> perms)
        throws IOException
    {
        Files.createFile(path);
        return Files.setPosixFilePermissions(path, perms);
    }

    private static Path doCreateDirectory(final Path dir,
        final Set<PosixFilePermission> perms)
        throws IOException
    {
        Files.createDirectory(dir);
        return Files.setPosixFilePermissions(dir, perms);
    }

    private static void doCreateDirectories(final Path realDir,
        final Set<PosixFilePermission> perms)
        throws IOException
    {
        final List<Path> created = new ArrayList<>();

        Path parent = realDir;

        while (parent != null && !Files.exists(parent)) {
            created.add(parent);
            parent = parent.getParent();
        }

        Files.createDirectories(realDir);

        for (final Path path: created)
            Files.setPosixFilePermissions(path, perms);
    }
    
    @Nonnull
    public static Path touch(final Path path)
        throws IOException
    {
        if (!Files.exists(path))
            return Files.createFile(path);

        final FileTime time = FileTime.fromMillis(System.currentTimeMillis());
        return setTimes(path, time);
    }

    @Nonnull
    static Path setTimes(final Path path, final FileTime fileTime)
        throws IOException
    {
        final BasicFileAttributeView view = Files.getFileAttributeView(path,
            BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        view.setTimes(fileTime, fileTime, null);
        return path;
    }
}

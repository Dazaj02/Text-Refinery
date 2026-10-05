package com.textrefinery.infrastructure.lock;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Garantiza una sola instancia de la app mediante un bloqueo de archivo (válido en los 3 SO). */
public class SingleInstanceLock {

    private final Path lockFile;
    private FileChannel channel;
    private FileLock lock;

    public SingleInstanceLock(Path lockFile) {
        this.lockFile = lockFile;
    }

    public boolean tryAcquire() {
        try {
            Files.createDirectories(lockFile.getParent());
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = channel.tryLock();
            return lock != null;
        } catch (IOException | java.nio.channels.OverlappingFileLockException e) {
            return false;
        }
    }

    public void release() {
        try {
            if (lock != null) {
                lock.release();
            }
            if (channel != null) {
                channel.close();
            }
        } catch (IOException ignored) {
            // al salir del proceso el SO libera el bloqueo de todos modos
        }
    }
}

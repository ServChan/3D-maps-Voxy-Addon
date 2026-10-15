package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class AtomicPropertiesWriter {
    private static final long DEBOUNCE_MILLIS = 100;
    private static final ScheduledThreadPoolExecutor WORKER = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "VoxyMap-Config-Writer");
        thread.setDaemon(true);
        return thread;
    });
    private static final Object LOCK = new Object();
    private static final Map<Path, PendingWrite> PENDING = new HashMap<>();

    static {
        WORKER.setRemoveOnCancelPolicy(true);
    }

    private AtomicPropertiesWriter() {
    }

    public static void save(Path target, Path backup, Path temporary, Properties properties, String comment) {
        Properties snapshot = new Properties();
        snapshot.putAll(properties);
        synchronized (LOCK) {
            PendingWrite previous = PENDING.remove(target);
            if (previous != null && previous.future != null) previous.future.cancel(false);
            PendingWrite pending = new PendingWrite(target, backup, temporary, snapshot, comment);
            pending.future = WORKER.schedule(() -> persistAndRemove(pending), DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
            PENDING.put(target, pending);
        }
    }

    public static void flush() {
        List<PendingWrite> writes;
        synchronized (LOCK) {
            writes = new ArrayList<>(PENDING.values());
            PENDING.clear();
            writes.forEach(write -> {
                if (write.future != null) write.future.cancel(false);
            });
        }
        try {
            Future<?> barrier = WORKER.submit(() -> writes.forEach(AtomicPropertiesWriter::persist));
            barrier.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            VoxyMapClient.LOGGER.error("Could not finish saving settings before shutdown", exception);
        }
    }

    private static void persistAndRemove(PendingWrite write) {
        synchronized (LOCK) {
            if (PENDING.get(write.target) != write) return;
            PENDING.remove(write.target);
        }
        persist(write);
    }

    private static void persist(PendingWrite write) {
        try {
            Files.createDirectories(write.target.getParent());
            try (FileOutputStream output = new FileOutputStream(write.temporary.toFile());
                 Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                write.properties.store(writer, write.comment);
                writer.flush();
                output.getFD().sync();
            }
            if (isReadableProperties(write.target)) {
                Files.copy(write.target, write.backup, StandardCopyOption.REPLACE_EXISTING);
            }
            replaceAtomically(write.temporary, write.target);
        } catch (IOException exception) {
            VoxyMapClient.LOGGER.error("Could not save {}", write.target, exception);
            try {
                Files.deleteIfExists(write.temporary);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
        }
    }

    public static boolean looksTruncated(Path path) {
        try {
            if (!Files.exists(path)) {
                return false;
            }
            byte[] bytes = Files.readAllBytes(path);
            for (byte value : bytes) {
                if (value != 0 && !Character.isWhitespace(value)) {
                    return false;
                }
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean isReadableProperties(Path path) {
        if (!Files.exists(path) || looksTruncated(path)) return false;
        try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            new Properties().load(reader);
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class PendingWrite {
        private final Path target;
        private final Path backup;
        private final Path temporary;
        private final Properties properties;
        private final String comment;
        private ScheduledFuture<?> future;

        private PendingWrite(Path target, Path backup, Path temporary, Properties properties, String comment) {
            this.target = target;
            this.backup = backup;
            this.temporary = temporary;
            this.properties = properties;
            this.comment = comment;
        }
    }
}

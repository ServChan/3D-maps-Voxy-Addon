package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class MapShareClient {
    private static final int MAGIC = 0x564D4150;
    private static final byte PROTOCOL_VERSION = 4;
    private static final int FIRST_VERSION_WITH_END_MARKER = 4;
    private static final int END_OF_SECTIONS = Integer.MIN_VALUE;
    private static final byte MIN_SUPPORTED_PROTOCOL_VERSION = 2;
    private static final int CHUNK_SECTION_VOXELS = 4096;
    private static final int PROGRESS_LOG_INTERVAL = 2000;
    private static final String ESTIMATED_BYTES_HEADER = "X-VoxyMap-Estimated-Bytes";
    private static final String SENDER_TOKEN_HEADER = "X-VoxyMap-Sender-Token";
    private static final String RECEIVER_TOKEN_HEADER = "X-VoxyMap-Receiver-Token";
    private static final long SHARE_RETRY_DELAY_MILLIS = 3000;

    private static final long CONFIRMATION_POLL_TIMEOUT_SECONDS = 130;
    private static final long CONFIRMATION_POLL_INTERVAL_MILLIS = 400;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONTROL_REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration TRANSFER_REQUEST_TIMEOUT = Duration.ofMinutes(31);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .executor(Executors.newFixedThreadPool(4, runnable -> {
                Thread thread = new Thread(runnable, "VoxyMap-MapShare-Http");
                thread.setDaemon(true);
                return thread;
            }))
            .build();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "VoxyMap-MapShare-Worker");
        thread.setDaemon(true);
        return thread;
    });

    public enum ShareStatus { IDLE, WAITING, AWAITING_CONFIRMATION, SENDING, ERROR }

    public enum ReceiveStatus { IDLE, CONNECTING, AWAITING_CONFIRMATION, RECEIVING, DONE, ERROR }

    private static volatile boolean sharingActive = false;
    private static volatile ShareStatus shareStatus = ShareStatus.IDLE;
    private static volatile int shareCompletedCount = 0;
    private static volatile int shareSectionsSentThisAttempt = 0;
    private static volatile int shareSectionsTotalThisAttempt = 0;
    private static volatile long shareEstimatedBytes = 0;
    private static volatile String shareCode = null;
    private static volatile String shareError = null;

    private static volatile WorldIdentifier sharingWorldId = null;
    private static volatile MapShareBounds sharingBounds = null;

    private static volatile ReceiveStatus receiveStatus = ReceiveStatus.IDLE;
    private static volatile int receiveImportedCount = 0;
    private static volatile int receiveSkippedCount = 0;
    private static volatile int receiveTotalCount = 0;
    private static volatile long receiveEstimatedBytes = 0;
    private static volatile String receiveError = null;
    private static volatile WorldIdentifier receivingWorldId = null;

    private static volatile String receivingBaseUrl = null;
    private static volatile String receivingCode = null;
    private static volatile String receivingToken = null;
    private static volatile int receiveSession = 0;
    private static volatile Future<?> activeShareTask = null;
    private static volatile Future<?> activeReceiveTask = null;

    private MapShareClient() {
    }

    public static boolean isSharing() {
        return sharingActive;
    }

    public static String shareCode() {
        return shareCode;
    }

    public static ShareStatus shareStatus() {
        return shareStatus;
    }

    public static int shareCompletedCount() {
        return shareCompletedCount;
    }

    public static int shareSectionsSentThisAttempt() {
        return shareSectionsSentThisAttempt;
    }

    public static int shareSectionsTotalThisAttempt() {
        return shareSectionsTotalThisAttempt;
    }

    public static long shareEstimatedBytes() {
        return shareEstimatedBytes;
    }

    public static String shareError() {
        return shareError;
    }

    public static String startSharing(Minecraft minecraft, String serverAddress) {
        return startSharing(minecraft, serverAddress, null);
    }

    public static String startSharing(Minecraft minecraft, String serverAddress, MapShareBounds bounds) {
        WorldIdentifier worldId = VoxySectionBridge.currentWorldIdentifier(minecraft);
        if (worldId == null) return null;
        if (sharingActive) {
            if (worldId.equals(sharingWorldId)) return shareCode;
            stopSharing();
        }
        String code = generateCode();
        shareCode = code;
        sharingWorldId = worldId;
        sharingBounds = bounds;
        sharingActive = true;
        shareStatus = ShareStatus.WAITING;
        shareCompletedCount = 0;
        shareSectionsSentThisAttempt = 0;
        shareError = null;
        String baseUrl = normalize(serverAddress);
        activeShareTask = WORKERS.submit(() -> shareLoop(worldId, bounds, baseUrl, code));
        return code;
    }

    public static void stopSharing() {
        sharingActive = false;
        shareStatus = ShareStatus.IDLE;
        shareCode = null;
        sharingWorldId = null;
        sharingBounds = null;
        if (activeShareTask != null) {
            activeShareTask.cancel(true);
            activeShareTask = null;
        }
    }

    public static void stopIfWorldChanged(Minecraft minecraft) {
        boolean receiveSessionActive = receiveStatus == ReceiveStatus.RECEIVING
                || receiveStatus == ReceiveStatus.AWAITING_CONFIRMATION
                || receiveStatus == ReceiveStatus.CONNECTING;
        if (!sharingActive && !receiveSessionActive) return;
        WorldIdentifier currentWorldId = VoxySectionBridge.currentWorldIdentifier(minecraft);
        if (sharingActive && !java.util.Objects.equals(sharingWorldId, currentWorldId)) {
            stopSharing();
        }
        if (receiveSessionActive && !java.util.Objects.equals(receivingWorldId, currentWorldId)) {
            stopReceiving();
            receiveStatus = ReceiveStatus.ERROR;
            receiveError = "World changed during receive";
        }
    }

    private static boolean isCurrentShare(String code) {
        return sharingActive && code.equals(shareCode);
    }

    private static void shareLoop(WorldIdentifier worldId, MapShareBounds bounds, String baseUrl, String code) {
        while (isCurrentShare(code) && !Thread.currentThread().isInterrupted()) {
            shareStatus = ShareStatus.WAITING;
            try {
                if (!sendOnce(worldId, bounds, baseUrl, code) && isCurrentShare(code)) {
                    sleepQuietly(SHARE_RETRY_DELAY_MILLIS);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (ShareRejectedException exception) {
                if (isCurrentShare(code)) {
                    shareError = exception.getMessage();
                    shareStatus = ShareStatus.ERROR;
                    VoxyMapClient.LOGGER.warn("[VoxyMap] Map share {} cannot start: {}", code, exception.getMessage());
                }
                break;
            } catch (IOException exception) {
                if (!isCurrentShare(code)) {
                    break;
                }
                shareError = exception.toString();
                shareStatus = ShareStatus.ERROR;
                VoxyMapClient.LOGGER.warn("[VoxyMap] Map share attempt failed for code {}: {}", code, exception.toString());
                sleepQuietly(SHARE_RETRY_DELAY_MILLIS);
            } catch (RuntimeException exception) {
                if (!isCurrentShare(code)) {
                    break;
                }
                shareError = exception.toString();
                shareStatus = ShareStatus.ERROR;
                VoxyMapClient.LOGGER.warn("[VoxyMap] Map share attempt failed with unexpected error for code {}: {}", code, exception.toString());
                sleepQuietly(SHARE_RETRY_DELAY_MILLIS);
            }
        }
    }

    private static boolean sendOnce(WorldIdentifier worldId, MapShareBounds bounds, String baseUrl, String code) throws IOException, InterruptedException {
        shareSectionsSentThisAttempt = 0;
        shareSectionsTotalThisAttempt = 0;
        shareEstimatedBytes = 0;

        long sectionCount = VoxySectionBridge.exportedSectionCount(worldId, bounds);
        if (sectionCount > MapShareProtocolLimits.MAX_CHUNK_SECTIONS / 8) {
            throw new ShareRejectedException("Selected area has " + sectionCount
                    + " explored sections; at most " + MapShareProtocolLimits.MAX_CHUNK_SECTIONS / 8 + " can be shared at once");
        }
        long estimatedBytes = MapShareProtocolLimits.estimatedBytes(sectionCount * VoxySectionBridge.EXPORT_BYTES_PER_SECTION);
        HttpRequest waitRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code + "/wait"))
                .header(ESTIMATED_BYTES_HEADER, Long.toString(estimatedBytes))
                .timeout(TRANSFER_REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        shareStatus = ShareStatus.WAITING;
        HttpResponse<Void> waitResponse = HTTP.send(waitRequest, HttpResponse.BodyHandlers.discarding());
        if (waitResponse.statusCode() == 408) {
            return true;
        }
        if (waitResponse.statusCode() != 200) {
            shareError = "HTTP " + waitResponse.statusCode();
            shareStatus = ShareStatus.ERROR;
            return false;
        }
        String token = waitResponse.headers().firstValue(SENDER_TOKEN_HEADER).orElse(null);
        if (token == null) {
            shareError = "Relay server did not return a sender token";
            shareStatus = ShareStatus.ERROR;
            return false;
        }

        shareEstimatedBytes = estimatedBytes;
        shareStatus = ShareStatus.AWAITING_CONFIRMATION;
        Boolean confirmed = pollForReceiverDecision(baseUrl, code, token);
        if (confirmed == null || !confirmed) {
            return true;
        }

        PipedOutputStream pipeOut = new PipedOutputStream();
        PipedInputStream pipeIn = new PipedInputStream(pipeOut, 1 << 16);

        AtomicReference<Throwable> exportFailure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(pipeOut, 1 << 16)));
                writeExport(out, worldId, bounds, code);
                out.close();
            } catch (IOException | RuntimeException | LinkageError exception) {
                exportFailure.set(exception);
                try {
                    pipeIn.close();
                } catch (IOException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
            }
        }, "VoxyMap-MapShare-Export");
        writer.setDaemon(true);
        writer.start();

        HttpRequest dataRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code))
                .header(SENDER_TOKEN_HEADER, token)
                .timeout(TRANSFER_REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> pipeIn))
                .build();
        shareStatus = ShareStatus.SENDING;
        HttpResponse<Void> dataResponse;
        try {
            dataResponse = HTTP.send(dataRequest, HttpResponse.BodyHandlers.discarding());
        } catch (IOException sendFailure) {
            Throwable failure = exportFailure.get();
            if (failure == null) throw sendFailure;
            IOException wrapped = new IOException("Map export failed: " + failure, failure);
            wrapped.addSuppressed(sendFailure);
            throw wrapped;
        } finally {
            pipeIn.close();
            writer.interrupt();
            writer.join(1000);
        }
        Throwable failure = exportFailure.get();
        if (failure != null && isCurrentShare(code)) {
            throw new IOException("Map export failed: " + failure, failure);
        }
        if (dataResponse.statusCode() == 200) {
            shareCompletedCount++;
            shareError = null;
            return true;
        }
        shareError = "HTTP " + dataResponse.statusCode();
        shareStatus = ShareStatus.ERROR;
        return false;
    }

    private static Boolean pollForReceiverDecision(String baseUrl, String code, String token) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CONFIRMATION_POLL_TIMEOUT_SECONDS);
        while (isCurrentShare(code) && System.nanoTime() < deadline) {
            try {
                HttpRequest statusRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code + "/status"))
                        .header(SENDER_TOKEN_HEADER, token)
                        .timeout(CONTROL_REQUEST_TIMEOUT).GET().build();
                HttpResponse<String> statusResponse = HTTP.send(statusRequest, HttpResponse.BodyHandlers.ofString());
                if (statusResponse.statusCode() == 404) {
                    return null;
                }
                if (statusResponse.statusCode() == 200) {
                    String body = statusResponse.body();
                    if (body.contains("declined=true")) return false;
                    if (body.contains("confirmed=true")) return true;
                }
            } catch (IOException transientFailure) {

            }
            Thread.sleep(CONFIRMATION_POLL_INTERVAL_MILLIS);
        }
        return null;
    }

    private static void writeExport(DataOutputStream out, WorldIdentifier worldId, MapShareBounds bounds, String code) throws IOException {
        out.writeInt(MAGIC);
        out.writeByte(PROTOCOL_VERSION);

        List<VoxySectionBridge.MappingRow> mappings = VoxySectionBridge.exportMappings(worldId);
        out.writeInt(MapShareProtocolLimits.mappingCount(mappings.size()));
        long mappingBytes = 0;
        for (VoxySectionBridge.MappingRow row : mappings) {
            out.writeInt(row.packedKey());
            int rowLength = MapShareProtocolLimits.mappingRowLength(row.data().length, mappingBytes);
            out.writeInt(rowLength);
            out.write(row.data());
            mappingBytes += rowLength;
        }

        long sectionCount = VoxySectionBridge.exportedSectionCount(worldId, bounds);
        VoxyMapClient.LOGGER.info("[VoxyMap] Map share {}: exporting {} explored level-0 sections{} ({} block/biome mapping rows)",
                code, sectionCount, bounds == null ? "" : " inside " + bounds, mappings.size());

        long chunkSectionCount;
        try {
            chunkSectionCount = Math.multiplyExact(sectionCount, 8L);
        } catch (ArithmeticException overflow) {
            throw new IOException("Map-share section count overflow", overflow);
        }
        if (chunkSectionCount > Integer.MAX_VALUE) {
            throw new IOException("Map-share selection contains too many sections");
        }
        int total = MapShareProtocolLimits.declaredSectionCount((int) chunkSectionCount);
        shareSectionsTotalThisAttempt = total;
        out.writeInt(total);

        int[] sent = {0};
        IOException[] failure = new IOException[1];
        VoxySectionBridge.forEachExportedSectionKey(worldId, bounds, sectionKey -> {
            if (failure[0] != null || !isCurrentShare(code) || Thread.currentThread().isInterrupted()
                    || sent[0] + 8 > total) {
                return;
            }
            try {
                long[] data = VoxySectionBridge.exportSectionData(worldId, sectionKey);
                if (data == null) {
                    return;
                }
                int wx = WorldEngine.getX(sectionKey);
                int wy = WorldEngine.getY(sectionKey);
                int wz = WorldEngine.getZ(sectionKey);
                long[][] chunks = VoxySectionBridge.splitIntoChunkSections(data);
                for (int sub = 0; sub < 8; sub++) {
                    int chunkX = (wx << 1) | (sub & 1);
                    int chunkZ = (wz << 1) | ((sub >> 1) & 1);
                    int chunkY = (wy << 1) | ((sub >> 2) & 1);
                    out.writeInt(chunkX);
                    out.writeInt(chunkY);
                    out.writeInt(chunkZ);
                    for (long voxel : chunks[sub]) {
                        out.writeLong(voxel);
                    }
                    sent[0]++;
                    shareSectionsSentThisAttempt = sent[0];
                    if (sent[0] % PROGRESS_LOG_INTERVAL == 0) {
                        VoxyMapClient.LOGGER.info("[VoxyMap] Map share {}: sent {} chunk-sections so far", code, sent[0]);
                    }
                }
            } catch (IOException exception) {
                failure[0] = exception;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        if (!isCurrentShare(code) || Thread.currentThread().isInterrupted()) {
            throw new IOException("Map share stopped before the export finished");
        }
        out.writeInt(END_OF_SECTIONS);
        VoxyMapClient.LOGGER.info("[VoxyMap] Map share {}: export finished, {} chunk-sections total", code, sent[0]);
    }

    public static ReceiveStatus receiveStatus() {
        return receiveStatus;
    }

    public static int receiveImportedCount() {
        return receiveImportedCount;
    }

    public static int receiveSkippedCount() {
        return receiveSkippedCount;
    }

    public static int receiveTotalCount() {
        return receiveTotalCount;
    }

    public static String receiveError() {
        return receiveError;
    }

    public static long receiveEstimatedBytes() {
        return receiveEstimatedBytes;
    }

    public static void startReceiving(Minecraft minecraft, String serverAddress, String code) {
        WorldIdentifier worldId = VoxySectionBridge.currentWorldIdentifier(minecraft);
        if (worldId == null) return;
        cancelActiveReceiveTask();
        int session = ++receiveSession;
        receiveStatus = ReceiveStatus.CONNECTING;
        receiveImportedCount = 0;
        receiveSkippedCount = 0;
        receiveTotalCount = 0;
        receiveEstimatedBytes = 0;
        receiveError = null;
        receivingWorldId = worldId;
        String baseUrl = normalize(serverAddress);
        receivingBaseUrl = baseUrl;
        receivingCode = code;
        receivingToken = null;
        activeReceiveTask = WORKERS.submit(() -> receiveInfoOnce(session, baseUrl, code));
    }

    public static void stopReceiving() {
        cancelActiveReceiveTask();
        receiveSession++;
        receiveStatus = ReceiveStatus.IDLE;
        receivingWorldId = null;
        receivingBaseUrl = null;
        receivingCode = null;
        receivingToken = null;
    }

    private static void cancelActiveReceiveTask() {
        Future<?> task = activeReceiveTask;
        if (task != null) {
            task.cancel(true);
            activeReceiveTask = null;
        }
    }

    private static boolean isCurrentReceive(int session) {
        return session == receiveSession;
    }

    private static synchronized void setReceiveStatus(int session, ReceiveStatus status) {
        if (isCurrentReceive(session)) {
            receiveStatus = status;
        }
    }

    private static synchronized void failReceive(int session, String error) {
        if (isCurrentReceive(session)) {
            receiveError = error;
            receiveStatus = ReceiveStatus.ERROR;
        }
    }

    private static void receiveInfoOnce(int session, String baseUrl, String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code + "/info"))
                    .timeout(CONTROL_REQUEST_TIMEOUT).GET().build();
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200) {
                failReceive(session, "HTTP " + response.statusCode());
                return;
            }
            String token = response.headers().firstValue(RECEIVER_TOKEN_HEADER).orElse(null);
            if (token == null) {
                failReceive(session, "Relay server did not return a receiver token");
                return;
            }
            long estimatedBytes = MapShareProtocolLimits.estimatedBytes(
                    response.headers().firstValue(ESTIMATED_BYTES_HEADER).orElse("0"));
            synchronized (MapShareClient.class) {
                if (!isCurrentReceive(session)) return;
                receiveEstimatedBytes = estimatedBytes;
                receivingToken = token;
                receiveStatus = ReceiveStatus.AWAITING_CONFIRMATION;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            setReceiveStatus(session, ReceiveStatus.IDLE);
        } catch (Exception exception) {
            failReceive(session, exception.toString());
            VoxyMapClient.LOGGER.warn("[VoxyMap] Map receive info for code {} failed: {}", code, exception.toString());
        }
    }

    public static void confirmReceive(Minecraft minecraft) {
        if (receiveStatus != ReceiveStatus.AWAITING_CONFIRMATION) return;
        int session = receiveSession;
        WorldIdentifier worldId = receivingWorldId;
        String baseUrl = receivingBaseUrl;
        String code = receivingCode;
        String token = receivingToken;
        receiveStatus = ReceiveStatus.CONNECTING;
        activeReceiveTask = WORKERS.submit(() -> {
            try {
                HttpRequest confirmRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code + "/confirm"))
                        .header(RECEIVER_TOKEN_HEADER, token)
                        .timeout(CONTROL_REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<Void> confirmResponse = HTTP.send(confirmRequest, HttpResponse.BodyHandlers.discarding());
                if (confirmResponse.statusCode() != 200) {
                    failReceive(session, "HTTP " + confirmResponse.statusCode());
                    return;
                }
                receiveDataOnce(session, minecraft, worldId, baseUrl, code, token);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                setReceiveStatus(session, ReceiveStatus.IDLE);
            } catch (Exception exception) {
                failReceive(session, exception.toString());
                VoxyMapClient.LOGGER.warn("[VoxyMap] Map receive confirm for code {} failed: {}", code, exception.toString());
            }
        });
    }

    public static void declineReceive() {
        if (receiveStatus != ReceiveStatus.AWAITING_CONFIRMATION) return;
        String baseUrl = receivingBaseUrl;
        String code = receivingCode;
        String token = receivingToken;
        stopReceiving();
        WORKERS.submit(() -> {
            try {
                HttpRequest declineRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code + "/decline"))
                        .header(RECEIVER_TOKEN_HEADER, token)
                        .timeout(CONTROL_REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                HTTP.send(declineRequest, HttpResponse.BodyHandlers.discarding());
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                VoxyMapClient.LOGGER.warn("[VoxyMap] Map receive decline for code {} failed: {}", code, exception.toString());
            }
        });
    }

    private static void receiveDataOnce(int session, Minecraft minecraft, WorldIdentifier worldId, String baseUrl,
                                        String code, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code))
                    .header(RECEIVER_TOKEN_HEADER, token)
                    .timeout(TRANSFER_REQUEST_TIMEOUT).GET().build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                failReceive(session, "HTTP " + response.statusCode());
                return;
            }
            setReceiveStatus(session, ReceiveStatus.RECEIVING);
            try (DataInputStream in = new DataInputStream(MapShareProtocolLimits.limitDecompressed(
                    new GZIPInputStream(new BufferedInputStream(response.body(), 1 << 16))))) {
                readImport(session, minecraft, worldId, in, code);
            }

            boolean finished;
            synchronized (MapShareClient.class) {
                finished = isCurrentReceive(session) && receiveStatus == ReceiveStatus.RECEIVING;
                if (finished) {
                    receiveStatus = ReceiveStatus.DONE;
                }
            }
            VoxyMapClient.LOGGER.info("[VoxyMap] Map receive {}: {}, {} imported, {} already had",
                    code, finished ? "done" : "stopped", receiveImportedCount, receiveSkippedCount);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            setReceiveStatus(session, ReceiveStatus.IDLE);
        } catch (Exception exception) {
            if (!isCurrentReceive(session)) return;
            failReceive(session, exception.toString());
            VoxyMapClient.LOGGER.warn("[VoxyMap] Map receive for code {} failed: {}", code, exception.toString());
        }
    }

    private static void readImport(int session, Minecraft minecraft, WorldIdentifier worldId, DataInputStream in,
                                   String code) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC) throw new IOException("Not a VoxyMap terrain stream");
        int version = in.readUnsignedByte();
        if (version < MIN_SUPPORTED_PROTOCOL_VERSION || version > PROTOCOL_VERSION) {
            throw new IOException("Unsupported protocol version " + version);
        }

        int mappingCount = MapShareProtocolLimits.mappingCount(in.readInt());
        List<VoxySectionBridge.MappingRow> mappings = new ArrayList<>(mappingCount);
        long mappingBytes = 0;
        for (int i = 0; i < mappingCount; i++) {
            int key = in.readInt();
            int length = MapShareProtocolLimits.mappingRowLength(in.readInt(), mappingBytes);
            byte[] data = new byte[length];
            in.readFully(data);
            mappings.add(new VoxySectionBridge.MappingRow(key, data));
            mappingBytes += length;
        }
        VoxySectionBridge.IdTranslation translation = VoxySectionBridge.buildTranslation(minecraft, worldId, mappings);

        int declaredTotal = version >= 3 ? MapShareProtocolLimits.declaredSectionCount(in.readInt()) : 0;
        if (!isCurrentReceive(session)) return;
        receiveTotalCount = declaredTotal;
        VoxyMapClient.LOGGER.info("[VoxyMap] Map receive {}: got {} mapping rows (sender protocol v{}), importing{}...",
                code, mappings.size(), version, declaredTotal > 0 ? " up to " + declaredTotal + " sections" : "");

        Map<VoxySectionBridge.WorldSectionKey, Integer> occupiedMaskCache = new HashMap<>();
        long[] voxels = new long[CHUNK_SECTION_VOXELS];
        int sectionIndex = 0;
        int imported = 0;
        int skipped = 0;
        while (isCurrentReceive(session) && receiveStatus == ReceiveStatus.RECEIVING) {
            int chunkX;
            try {
                chunkX = in.readInt();
            } catch (EOFException endOfStream) {
                if (version >= FIRST_VERSION_WITH_END_MARKER) {
                    throw new IOException("Map-share stream ended before the sender finished", endOfStream);
                }
                break;
            }
            if (version >= FIRST_VERSION_WITH_END_MARKER && chunkX == END_OF_SECTIONS) {
                break;
            }
            int chunkY = in.readInt();
            int chunkZ = in.readInt();
            MapShareProtocolLimits.sectionIndex(sectionIndex, declaredTotal);
            MapShareProtocolLimits.coordinates(chunkX, chunkY, chunkZ);
            for (int i = 0; i < voxels.length; i++) {
                voxels[i] = in.readLong();
            }
            if (!isCurrentReceive(session)) {
                break;
            }
            if (VoxySectionBridge.importChunkSection(worldId, chunkX, chunkY, chunkZ, voxels, translation, occupiedMaskCache)) {
                imported++;
            } else {
                skipped++;
            }
            if (isCurrentReceive(session)) {
                receiveImportedCount = imported;
                receiveSkippedCount = skipped;
            }
            sectionIndex++;
            if ((imported + skipped) % PROGRESS_LOG_INTERVAL == 0) {
                VoxyMapClient.LOGGER.info("[VoxyMap] Map receive {}: {} imported, {} already had, still going...",
                        code, imported, skipped);
            }
        }
    }

    private static final class ShareRejectedException extends IOException {
        private ShareRejectedException(String message) {
            super(message);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String generateCode() {
        return String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1_000_000));
    }

    private static String normalize(String address) {
        String trimmed = address.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        return trimmed;
    }
}

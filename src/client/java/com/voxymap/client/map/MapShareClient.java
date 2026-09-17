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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Client side of the "share my explored Voxy terrain with a code" feature - see
 * {@code MapRelayHandler}'s javadoc in the sync server module for the full wire-level relay handshake
 * this drives ({@code wait}/{@code info}/{@code status}/{@code confirm}/{@code decline}, then the actual
 * data connection), which is a live byte pipe the server never stores.
 *
 * <p>Both {@link #sendOnce} and the receiver's {@link #confirmReceive}/{@link #declineReceive} flow
 * mirror that handshake: a sender registers and waits to be claimed, the claiming receiver is shown an
 * estimated size and must explicitly confirm before the sender starts compressing or sending a single
 * byte of real terrain data, and the sender watches that decision happen live by polling {@code /status}
 * (see {@link #pollForReceiverDecision}) rather than through one giant blocking call. Declining, an
 * expired claim, or the confirmation window elapsing without a decision all fold back into the sender's
 * normal retry loop the same way a plain timeout would.
 *
 * <p>Stream format written by the sender and read by the receiver, once both sides have agreed to the
 * transfer: the whole body is gzip-compressed
 * (voxel data is mostly repeated air/stone/etc, so this shrinks a real transfer drastically - without
 * it, a heavily-explored world can be many gigabytes and a "share" can look hung for many minutes when
 * it's actually just uploading). Inside the decompressed stream, all big-endian
 * ({@link DataOutputStream}/{@link DataInputStream} defaults):
 * <pre>
 *   int    magic = 0x564D4150 ("VMAP")
 *   byte   protocol version (currently 3 - see below for what changed since 2)
 *   int    mapping row count N
 *   N *  { int packedKey, int byteLength, byte[byteLength] data }   -- see VoxySectionBridge.MappingRow
 *   int    total chunk-section count (upper bound - see writeExport)   -- version 3 only, absent in 2
 *   repeated until end of stream:
 *     int chunkX, int chunkY, int chunkZ                            -- chunk-section coordinates
 *     long[4096] voxels                                             -- level 0, sender's own local ids
 * </pre>
 *
 * <p>We always <em>write</em> the current version, but a receiver on this build still reads any sender
 * back to {@link #MIN_SUPPORTED_PROTOCOL_VERSION} - useful when a "share" and "receive" happen across a
 * staggered mod update (one player restarted with the new jar, the other hasn't yet). A version-2 sender
 * simply never gets a progress percentage (there's no total to compute it from, see
 * {@link #receiveTotalCount()}); everything else about the transfer works the same either way. There is
 * no such thing as a receiver reading a <em>newer</em> version than it understands - that still fails
 * with "Unsupported protocol version", since there is no way to know what an unreleased future field
 * might mean.
 *
 * <p>The mapping table is small (the vocabulary of distinct block/biome types, not per-voxel) and is
 * sent once up front; the receiver builds its own translation from it before importing any section, so
 * every voxel gets re-mapped into ids that mean the same real block/biome in the receiving world.
 */
public final class MapShareClient {
    private static final int MAGIC = 0x564D4150;
    private static final byte PROTOCOL_VERSION = 3; // 2: gzip-compressed body. 3: adds a total chunk-section count for progress %.
    private static final byte MIN_SUPPORTED_PROTOCOL_VERSION = 2; // Oldest sender version we can still read - see the class doc.
    private static final int CHUNK_SECTION_VOXELS = 4096;
    private static final int PROGRESS_LOG_INTERVAL = 2000;
    private static final String ESTIMATED_BYTES_HEADER = "X-VoxyMap-Estimated-Bytes";
    private static final String SENDER_TOKEN_HEADER = "X-VoxyMap-Sender-Token";
    // How long the sender will keep polling /status for the receiver's decision before giving up on this
    // attempt and looping back to waiting for a new claimant - a bit past the relay server's own
    // abandoned-claim reaper (MapRelayHandler.CONFIRMATION_TIMEOUT_SECONDS), so the server is always the
    // one to actually free the code first.
    private static final long CONFIRMATION_POLL_TIMEOUT_SECONDS = 130;
    private static final long CONFIRMATION_POLL_INTERVAL_MILLIS = 400;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONTROL_REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration TRANSFER_REQUEST_TIMEOUT = Duration.ofMinutes(31);

    // HTTP/1.1 is forced: the relay server is a plain com.sun.net.httpserver.HttpServer that never
    // speaks h2c, but HttpClient's default HTTP_2 preference still attempts a cleartext upgrade
    // negotiation on every request - combined with our chunked/streamed request body, that negotiation
    // hangs forever instead of falling back, which is what "infinite connecting" turned out to be.
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
    // The world "Share" was started for - see startSharing and stopIfWorldChanged. Sharing must never
    // outlive this world: it's a live, unattended export of that world's terrain to anyone with the
    // code, and nothing about leaving the world (disconnect, quit to title, join a different one) stops
    // it on its own - the background loop just keeps running against whatever WorldIdentifier it was
    // given, for as long as the game process is up, unless something actively tells it to stop.
    private static volatile WorldIdentifier sharingWorldId = null;
    private static volatile MapShareBounds sharingBounds = null;

    private static volatile ReceiveStatus receiveStatus = ReceiveStatus.IDLE;
    private static volatile int receiveImportedCount = 0;
    private static volatile int receiveSkippedCount = 0;
    private static volatile int receiveTotalCount = 0;
    private static volatile long receiveEstimatedBytes = 0;
    private static volatile String receiveError = null;
    private static volatile WorldIdentifier receivingWorldId = null;
    // Stashed by startReceiving so confirmReceive/declineReceive (triggered later, by a UI button click,
    // not part of the same call chain) know where to send the receiver's decision.
    private static volatile String receivingBaseUrl = null;
    private static volatile String receivingCode = null;
    private static Future<?> activeShareTask = null;
    private static Future<?> activeReceiveTask = null;

    private MapShareClient() {
    }

    // ---- Sender ----

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

    /** Chunk-sections written so far in the transfer currently in progress (resets each attempt) - the
     * only way to tell a slow multi-gigabyte upload apart from a stuck one before it finishes. */
    public static int shareSectionsSentThisAttempt() {
        return shareSectionsSentThisAttempt;
    }

    /** Total chunk-sections this attempt will send (an upper bound - see {@link #writeExport}), known
     * as soon as export starts. 0 until then. */
    public static int shareSectionsTotalThisAttempt() {
        return shareSectionsTotalThisAttempt;
    }

    /** Upper-bound estimate of this attempt's size, shown alongside the confirmation prompt - see
     * {@link VoxySectionBridge#estimateExportBytes}. 0 until a receiver has claimed the code. */
    public static long shareEstimatedBytes() {
        return shareEstimatedBytes;
    }

    public static String shareError() {
        return shareError;
    }

    /** Starts (or, if already sharing this same world, returns the existing) code and begins repeatedly
     * offering fresh exports to whoever connects with it, until {@link #stopSharing()} is called or the
     * player leaves this world (see {@link #stopIfWorldChanged}). If sharing was already active for a
     * <em>different</em> world - most likely because the player forgot to stop it before disconnecting
     * and has since joined somewhere else - that stale session is stopped first, rather than silently
     * handing back its leftover code while continuing to export the wrong world in the background. */
    public static String startSharing(Minecraft minecraft, String serverAddress) {
        return startSharing(minecraft, serverAddress, null);
    }

    /** Starts a share limited to the selected horizontal Voxy-section rectangle. A null selection is
     * retained for protocol/backwards compatibility but the map UI always supplies explicit bounds. */
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

    /** Call every client tick. Stops an active share/receive the moment it no longer belongs to the
     * world the player is actually in - see the warning on {@link #sharingWorldId}. This is the only
     * thing that makes leaving a world a reliable way to stop sharing it; nothing about disconnecting,
     * quitting to title, or joining a different world does that on its own otherwise, since the share
     * loop is a plain background thread with no lifecycle tie to any particular world or connection. */
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

    private static void shareLoop(WorldIdentifier worldId, MapShareBounds bounds, String baseUrl, String code) {
        while (sharingActive) {
            shareStatus = ShareStatus.WAITING;
            try {
                sendOnce(worldId, bounds, baseUrl, code);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException exception) {
                if (!sharingActive) {
                    break;
                }
                shareError = exception.toString();
                shareStatus = ShareStatus.ERROR;
                VoxyMapClient.LOGGER.warn("[VoxyMap] Map share attempt failed for code {}: {}", code, exception.toString());
                sleepQuietly(3000);
            } catch (RuntimeException exception) {
                if (!sharingActive) {
                    break;
                }
                shareError = exception.toString();
                shareStatus = ShareStatus.ERROR;
                VoxyMapClient.LOGGER.warn("[VoxyMap] Map share attempt failed with unexpected error for code {}: {}", code, exception.toString());
                sleepQuietly(3000);
            }
        }
    }

    /** One full attempt: register and wait to be claimed, show the receiver's decision live once
     * claimed, and only once they've actually confirmed does any real export work (compressing,
     * streaming voxel data) begin - see the class doc and {@code MapRelayHandler}'s. */
    private static void sendOnce(WorldIdentifier worldId, MapShareBounds bounds, String baseUrl, String code) throws IOException, InterruptedException {
        shareSectionsSentThisAttempt = 0;
        shareSectionsTotalThisAttempt = 0;
        shareEstimatedBytes = 0;

        long estimatedBytes = MapShareProtocolLimits.estimatedBytes(
                VoxySectionBridge.estimateExportBytes(worldId, bounds));
        HttpRequest waitRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code + "/wait"))
                .header(ESTIMATED_BYTES_HEADER, Long.toString(estimatedBytes))
                .timeout(TRANSFER_REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        shareStatus = ShareStatus.WAITING;
        HttpResponse<Void> waitResponse = HTTP.send(waitRequest, HttpResponse.BodyHandlers.discarding());
        if (waitResponse.statusCode() == 408) {
            return; // Nobody claimed it in time - not a failure worth surfacing, the loop just retries.
        }
        if (waitResponse.statusCode() != 200) {
            shareError = "HTTP " + waitResponse.statusCode();
            shareStatus = ShareStatus.ERROR;
            return;
        }
        String token = waitResponse.headers().firstValue(SENDER_TOKEN_HEADER).orElse(null);
        if (token == null) {
            shareError = "Relay server did not return a sender token";
            shareStatus = ShareStatus.ERROR;
            return;
        }

        shareEstimatedBytes = estimatedBytes;
        shareStatus = ShareStatus.AWAITING_CONFIRMATION;
        Boolean confirmed = pollForReceiverDecision(baseUrl, code);
        if (confirmed == null || !confirmed) {
            return; // Declined, or the claim was abandoned/expired - the loop retries with a fresh code offer.
        }

        PipedOutputStream pipeOut = new PipedOutputStream();
        PipedInputStream pipeIn = new PipedInputStream(pipeOut, 1 << 16);

        Thread writer = new Thread(() -> {
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(pipeOut, 1 << 16)))) {
                writeExport(out, worldId, bounds, code);
            } catch (IOException ignored) {
                // The read side (HttpClient reading pipeIn) surfaces this as a failed/short request.
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
        } finally {
            pipeIn.close();
            writer.interrupt();
            writer.join(1000);
        }
        if (dataResponse.statusCode() == 200) {
            shareCompletedCount++;
            shareError = null;
        } else {
            shareError = "HTTP " + dataResponse.statusCode();
            shareStatus = ShareStatus.ERROR;
        }
    }

    /** Polls /status until the receiver confirms (true), declines (false), or the claim vanishes/expires
     * or this share session gets stopped (null) - see the class doc on why this isn't just one blocking
     * server call. */
    private static Boolean pollForReceiverDecision(String baseUrl, String code) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CONFIRMATION_POLL_TIMEOUT_SECONDS);
        while (sharingActive && System.nanoTime() < deadline) {
            try {
                HttpRequest statusRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code + "/status"))
                        .timeout(CONTROL_REQUEST_TIMEOUT).GET().build();
                HttpResponse<String> statusResponse = HTTP.send(statusRequest, HttpResponse.BodyHandlers.ofString());
                if (statusResponse.statusCode() == 404) {
                    return null; // Declined (frees the code immediately) or reaped as abandoned.
                }
                if (statusResponse.statusCode() == 200) {
                    String body = statusResponse.body();
                    if (body.contains("declined=true")) return false;
                    if (body.contains("confirmed=true")) return true;
                }
            } catch (IOException transientFailure) {
                // Keep polling - a single dropped poll isn't worth aborting the whole attempt over.
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

        // Upper bound, not exact - a key can still turn out to have no data by the time we get to it
        // (see the null check below) and yield fewer than 8 chunk-sections. Good enough for a progress %.
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

        // Streamed via forEachExportedSectionKey rather than collecting every key into a list first - a
        // heavily-explored world can have millions of sections, and holding them all live at once as a
        // list is a large, long-lived allocation that can cause a visible GC pause even on this
        // background thread. iteratePositions (underneath) has no way to stop early, so a thrown
        // IOException is captured here and re-raised after the loop instead of propagating through the
        // LongConsumer signature, which can't declare it.
        int[] sent = {0};
        IOException[] failure = new IOException[1];
        VoxySectionBridge.forEachExportedSectionKey(worldId, bounds, sectionKey -> {
            if (failure[0] != null || !sharingActive || Thread.currentThread().isInterrupted()) {
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
        VoxyMapClient.LOGGER.info("[VoxyMap] Map share {}: export finished, {} chunk-sections total", code, sent[0]);
    }

    // ---- Receiver ----

    public static ReceiveStatus receiveStatus() {
        return receiveStatus;
    }

    public static int receiveImportedCount() {
        return receiveImportedCount;
    }

    public static int receiveSkippedCount() {
        return receiveSkippedCount;
    }

    /** Total chunk-sections the sender said it would send (an upper bound), known once the header has
     * been read. 0 until then. */
    public static int receiveTotalCount() {
        return receiveTotalCount;
    }

    public static String receiveError() {
        return receiveError;
    }

    /** Upper-bound estimate of what this code's transfer would be, shown alongside the confirmation
     * prompt - the sender's own estimate, learned from /info. 0 until claimed. */
    public static long receiveEstimatedBytes() {
        return receiveEstimatedBytes;
    }

    /** Claims the code and learns its size - does not touch any terrain data yet. Ends in either
     * {@link ReceiveStatus#AWAITING_CONFIRMATION} (call {@link #confirmReceive} or {@link #declineReceive}
     * next) or {@link ReceiveStatus#ERROR}. */
    public static void startReceiving(Minecraft minecraft, String serverAddress, String code) {
        WorldIdentifier worldId = VoxySectionBridge.currentWorldIdentifier(minecraft);
        if (worldId == null) return;
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
        activeReceiveTask = WORKERS.submit(() -> receiveInfoOnce(baseUrl, code));
    }

    public static void stopReceiving() {
        if (activeReceiveTask != null) {
            activeReceiveTask.cancel(true);
            activeReceiveTask = null;
        }
        receiveStatus = ReceiveStatus.IDLE;
        receivingWorldId = null;
        receivingBaseUrl = null;
        receivingCode = null;
    }

    private static void receiveInfoOnce(String baseUrl, String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code + "/info"))
                    .timeout(CONTROL_REQUEST_TIMEOUT).GET().build();
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200) {
                receiveError = "HTTP " + response.statusCode();
                receiveStatus = ReceiveStatus.ERROR;
                return;
            }
            receiveEstimatedBytes = MapShareProtocolLimits.estimatedBytes(
                    response.headers().firstValue(ESTIMATED_BYTES_HEADER).orElse("0"));
            receiveStatus = ReceiveStatus.AWAITING_CONFIRMATION;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            receiveStatus = ReceiveStatus.IDLE;
        } catch (Exception exception) {
            receiveError = exception.toString();
            receiveStatus = ReceiveStatus.ERROR;
            VoxyMapClient.LOGGER.warn("[VoxyMap] Map receive info for code {} failed: {}", code, exception.toString());
        }
    }

    /** Call once the local player has agreed to receive the size shown alongside
     * {@link ReceiveStatus#AWAITING_CONFIRMATION} - starts the actual data fetch and import. */
    public static void confirmReceive(Minecraft minecraft) {
        if (receiveStatus != ReceiveStatus.AWAITING_CONFIRMATION) return;
        WorldIdentifier worldId = receivingWorldId;
        String baseUrl = receivingBaseUrl;
        String code = receivingCode;
        activeReceiveTask = WORKERS.submit(() -> {
            try {
                HttpRequest confirmRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code + "/confirm"))
                        .timeout(CONTROL_REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<Void> confirmResponse = HTTP.send(confirmRequest, HttpResponse.BodyHandlers.discarding());
                if (confirmResponse.statusCode() != 200) {
                    receiveError = "HTTP " + confirmResponse.statusCode();
                    receiveStatus = ReceiveStatus.ERROR;
                    return;
                }
                receiveDataOnce(minecraft, worldId, baseUrl, code);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                receiveStatus = ReceiveStatus.IDLE;
            } catch (Exception exception) {
                receiveError = exception.toString();
                receiveStatus = ReceiveStatus.ERROR;
                VoxyMapClient.LOGGER.warn("[VoxyMap] Map receive confirm for code {} failed: {}", code, exception.toString());
            }
        });
    }

    /** Call once the local player has declined the size shown alongside
     * {@link ReceiveStatus#AWAITING_CONFIRMATION} - frees the code on the relay server immediately. */
    public static void declineReceive() {
        if (receiveStatus != ReceiveStatus.AWAITING_CONFIRMATION) return;
        String baseUrl = receivingBaseUrl;
        String code = receivingCode;
        stopReceiving();
        WORKERS.submit(() -> {
            try {
                HttpRequest declineRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code + "/decline"))
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

    private static void receiveDataOnce(Minecraft minecraft, WorldIdentifier worldId, String baseUrl, String code) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/relay/" + code))
                    .timeout(TRANSFER_REQUEST_TIMEOUT).GET().build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                receiveError = "HTTP " + response.statusCode();
                receiveStatus = ReceiveStatus.ERROR;
                return;
            }
            receiveStatus = ReceiveStatus.RECEIVING;
            try (DataInputStream in = new DataInputStream(MapShareProtocolLimits.limitDecompressed(
                    new GZIPInputStream(new BufferedInputStream(response.body(), 1 << 16))))) {
                readImport(minecraft, worldId, in, code);
            }
            // stopIfWorldChanged can have already moved this to ERROR mid-loop (readImport's while
            // condition checks for that and returns early) - don't stomp that verdict with DONE.
            if (receiveStatus == ReceiveStatus.RECEIVING) {
                receiveStatus = ReceiveStatus.DONE;
            }
            VoxyMapClient.LOGGER.info("[VoxyMap] Map receive {}: {}, {} imported, {} already had",
                    code, receiveStatus == ReceiveStatus.DONE ? "done" : "stopped", receiveImportedCount, receiveSkippedCount);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            receiveStatus = ReceiveStatus.IDLE;
        } catch (Exception exception) {
            receiveError = exception.toString();
            receiveStatus = ReceiveStatus.ERROR;
            VoxyMapClient.LOGGER.warn("[VoxyMap] Map receive for code {} failed: {}", code, exception.toString());
        }
    }

    private static void readImport(Minecraft minecraft, WorldIdentifier worldId, DataInputStream in, String code) throws IOException {
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
        // Version 3 added this field - an older sender's stream jumps straight from the mapping table
        // into chunk-sections, so reading it unconditionally would misparse the first one's chunkX.
        receiveTotalCount = version >= 3 ? MapShareProtocolLimits.declaredSectionCount(in.readInt()) : 0;
        VoxyMapClient.LOGGER.info("[VoxyMap] Map receive {}: got {} mapping rows (sender protocol v{}), importing{}...",
                code, mappings.size(), version, receiveTotalCount > 0 ? " up to " + receiveTotalCount + " sections" : "");

        // Shared across the whole session - see the "already explored" cache note on importChunkSection.
        Map<VoxySectionBridge.WorldSectionKey, Boolean> worldSectionCache = new HashMap<>();
        long[] voxels = new long[CHUNK_SECTION_VOXELS];
        int sectionIndex = 0;
        while (receiveStatus == ReceiveStatus.RECEIVING) {
            int chunkX;
            try {
                chunkX = in.readInt();
            } catch (EOFException endOfStream) {
                break;
            }
            int chunkY = in.readInt();
            int chunkZ = in.readInt();
            MapShareProtocolLimits.sectionIndex(sectionIndex, receiveTotalCount);
            MapShareProtocolLimits.coordinates(chunkX, chunkY, chunkZ);
            for (int i = 0; i < voxels.length; i++) {
                voxels[i] = in.readLong();
            }
            if (VoxySectionBridge.importChunkSection(worldId, chunkX, chunkY, chunkZ, voxels, translation, worldSectionCache)) {
                receiveImportedCount++;
            } else {
                receiveSkippedCount++;
            }
            sectionIndex++;
            int total = receiveImportedCount + receiveSkippedCount;
            if (total % PROGRESS_LOG_INTERVAL == 0) {
                VoxyMapClient.LOGGER.info("[VoxyMap] Map receive {}: {} imported, {} already had, still going...",
                        code, receiveImportedCount, receiveSkippedCount);
            }
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

package com.voxymap.syncserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Pairs one sender with one receiver by a short numeric code and pipes bytes live between their two
 * HTTP connections - the server never buffers or stores the transferred data on disk, it only holds the
 * pairing state for as long as a transfer is in flight. Backs the "share my explored Voxy terrain with
 * a code" flow. Both sides get a chance to see how much data is about to move and explicitly agree to it
 * before a single byte of real terrain data touches this server:
 *
 * <pre>
 *   POST /relay/{code}/wait     (sender)   - registers the code (with an estimated byte count in the
 *                                            X-VoxyMap-Estimated-Bytes header) and blocks until a
 *                                            receiver claims it, then returns 200 (claimed - go poll
 *                                            /status for their decision) with an X-VoxyMap-Sender-Token
 *                                            header identifying this registration - required by the
 *                                            data POST below - or 408 (nobody claimed it in the
 *                                            sender's own wait timeout).
 *   GET  /relay/{code}/info     (receiver) - claims a waiting sender's code and returns its estimated
 *                                            byte count, without touching any terrain data.
 *   GET  /relay/{code}/status   (either)   - read-only, side-effect-free peek at whether the code is
 *                                            claimed yet and what the receiver has decided so far -
 *                                            what the sender polls after "wait" returns to show the
 *                                            receiver's decision live.
 *   POST /relay/{code}/confirm  (receiver) - the receiver agreed to receive the estimated data.
 *   POST /relay/{code}/decline  (receiver) - the receiver said no; frees the code immediately.
 *   POST /relay/{code}          (sender)   - the actual data connection. Only valid once the receiver
 *                                            has confirmed and the caller presents the matching
 *                                            X-VoxyMap-Sender-Token from this code's own phase-1 call -
 *                                            this is the connection whose body is the real terrain
 *                                            export, opened only after that.
 *   GET  /relay/{code}          (receiver) - the actual data fetch. Only valid after this same receiver
 *                                            has confirmed; streams directly from the sender's still-
 *                                            open data connection into this response body.
 * </pre>
 *
 * <p>Splitting "who's there and how much data is this" from "the actual transfer" this way means the
 * sender's real export never starts compressing or writing a single byte - and this server's TCP stack
 * never buffers a single byte of it - until a receiver has been shown the size and explicitly agreed to
 * it. An earlier, single-phase version of this class let the sender's HTTP client start streaming the
 * real payload the instant "share" was clicked, before anyone had even seen the code, which was
 * confusing (nothing was ever readable without the code, but it looked like data was already "sent
 * somewhere") - this shape makes "nothing moves until both sides agree" literally true, not just true
 * in effect.
 *
 * <p>A sender that finishes serving one receiver is expected to immediately re-{@code POST} phase 1
 * under the same code to serve the next one, for as long as its "share" session stays open - the server
 * itself never remembers a code once its current transfer (or wait) ends, so a completed transfer
 * doesn't keep the code reserved.
 */
final class MapRelayHandler implements HttpHandler {
    private static final Pattern CODE_PATTERN = Pattern.compile("[0-9]{6}");
    private static final String ESTIMATED_BYTES_HEADER = "X-VoxyMap-Estimated-Bytes";
    private static final String SENDER_TOKEN_HEADER = "X-VoxyMap-Sender-Token";
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    // A safety net against a crashed/abandoned sender leaking its map entry forever - not the intended
    // lifetime of a code, which is however long the client keeps re-posting for.
    private static final long SENDER_WAIT_TIMEOUT_SECONDS = 1800;
    // How long the receiver (once it has confirmed) will wait for the sender's data connection to
    // actually show up. This handoff is normally near-instant (the client opens it the moment it sees
    // the receiver's confirmation) so this only needs to absorb real network latency, not anywhere near
    // SENDER_WAIT_TIMEOUT_SECONDS.
    private static final long DATA_CONNECT_TIMEOUT_SECONDS = 30;
    // A safety net against a receiver that confirms and then disappears (crash, lost network, closed
    // the game) before ever opening the actual GET that would count sender.finished down - without a
    // bound here that thread, its socket, and this pending entry (receiverConfirmed==true skips the
    // claim reaper below) would leak forever. Generous like SENDER_WAIT_TIMEOUT_SECONDS above so a
    // legitimately large, slow transfer isn't cut off mid-stream.
    private static final long TRANSFER_TIMEOUT_SECONDS = 1800;
    // How long a claimed-but-undecided code is allowed to sit before the reaper below frees it. This is
    // the one abandonment path nothing else here bounds: /info already returned (nothing left blocking
    // to time it out) and the receiver may just never call /confirm or /decline at all - they closed the
    // game, got distracted, whatever - so this map entry would otherwise sit "pending" forever.
    private static final long CONFIRMATION_TIMEOUT_SECONDS = 120;
    private static final long REAP_INTERVAL_SECONDS = 30;
    private static final long MAX_ESTIMATED_BYTES = 1L << 40; // 1 TiB is already far beyond a practical transfer.
    private static final int CLAIM_ATTEMPTS_PER_MINUTE = 120;

    private final ConcurrentHashMap<String, PendingSender> pending = new ConcurrentHashMap<>();
    private final RelayClaimRateLimiter claimRateLimiter =
            new RelayClaimRateLimiter(CLAIM_ATTEMPTS_PER_MINUTE, 1, TimeUnit.MINUTES);
    private final ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "voxymap-relay-reaper");
        thread.setDaemon(true);
        return thread;
    });

    MapRelayHandler() {
        reaper.scheduleWithFixedDelay(this::reapAbandonedClaims, REAP_INTERVAL_SECONDS, REAP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void reapAbandonedClaims() {
        claimRateLimiter.prune();
        long now = System.nanoTime();
        for (Map.Entry<String, PendingSender> entry : pending.entrySet()) {
            PendingSender sender = entry.getValue();
            if (sender.claimedFlag.get() && !sender.receiverConfirmed && !sender.receiverDeclined
                    && now - sender.claimedAtNanos > TimeUnit.SECONDS.toNanos(CONFIRMATION_TIMEOUT_SECONDS)) {
                pending.remove(entry.getKey(), sender);
            }
        }
    }

    private static final class PendingSender {
        final CountDownLatch claimed = new CountDownLatch(1);
        final CountDownLatch dataReady = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicBoolean claimedFlag = new AtomicBoolean(false);
        // Separate from claimedFlag: /info claims the code, but the actual data fetch is a later, second
        // call - without its own guard, two concurrent GETs to the bare data endpoint (a double-click, a
        // retry, or a stranger who saw the code and guessed the bare URL) could both pass the
        // claimed+confirmed check and race to read the same sender connection at once.
        final AtomicBoolean dataFetchStarted = new AtomicBoolean(false);
        // Binds the phase-2 data POST to the phase-1 caller that registered this code - otherwise,
        // since phase 2 is now a separate later call, anyone who merely knows the code could race to
        // supply their own data as soon as the receiver confirms, impersonating the real sender. The
        // single-call design this replaced didn't have that window: registering and sending were the
        // same connection, so the first POST to arrive always was the sender, unimpersonably.
        final String senderToken = generateToken();
        volatile long estimatedBytes;
        volatile long claimedAtNanos;
        volatile boolean receiverConfirmed = false;
        volatile boolean receiverDeclined = false;
        volatile HttpExchange dataExchange;
        volatile boolean transferSucceeded = false;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (RuntimeException exception) {
            System.err.println("[voxymap-sync-server] Relay error handling " + exchange.getRequestURI() + ": " + exception);
            try {
                respond(exchange, 500);
            } catch (IOException | RuntimeException ignored) {
                // Best-effort - the connection may already be in a bad state.
            }
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        // Path is "/relay/{code}" or "/relay/{code}/{action}" - segments[0] is the empty string before
        // the leading slash, segments[1] is "relay".
        String[] segments = exchange.getRequestURI().getPath().split("/");
        String code = segments.length > 2 ? segments[2] : null;
        String action = segments.length > 3 ? segments[3] : null;
        if (code == null || !CODE_PATTERN.matcher(code).matches() || segments.length > 4) {
            respond(exchange, 404);
            return;
        }

        String method = exchange.getRequestMethod();
        if (action == null) {
            if ("POST".equals(method)) {
                handleSendData(exchange, code);
            } else if ("GET".equals(method)) {
                handleReceiveData(exchange, code);
            } else {
                respond(exchange, 405);
            }
            return;
        }
        switch (action) {
            case "wait" -> {
                if (!"POST".equals(method)) { respond(exchange, 405); return; }
                handleSendWait(exchange, code);
            }
            case "info" -> {
                if (!"GET".equals(method)) { respond(exchange, 405); return; }
                handleReceiveInfo(exchange, code);
            }
            case "status" -> {
                if (!"GET".equals(method)) { respond(exchange, 405); return; }
                handleStatus(exchange, code);
            }
            case "confirm" -> {
                if (!"POST".equals(method)) { respond(exchange, 405); return; }
                handleReceiverDecision(exchange, code, true);
            }
            case "decline" -> {
                if (!"POST".equals(method)) { respond(exchange, 405); return; }
                handleReceiverDecision(exchange, code, false);
            }
            default -> respond(exchange, 404);
        }
    }

    /** Sender phase 1: register the code (with its size estimate) and wait to be claimed. */
    private void handleSendWait(HttpExchange exchange, String code) throws IOException {
        PendingSender sender = new PendingSender();
        sender.estimatedBytes = parseEstimatedBytes(exchange);
        if (sender.estimatedBytes < 0) {
            respond(exchange, 400);
            return;
        }
        if (pending.putIfAbsent(code, sender) != null) {
            respond(exchange, 409); // This code already has a sender waiting or transferring.
            return;
        }
        if (!await(sender.claimed, SENDER_WAIT_TIMEOUT_SECONDS)) {
            pending.remove(code, sender);
            respond(exchange, 408); // No receiver claimed it in time.
            return;
        }
        // Claimed - the client is expected to now poll /status for the receiver's decision, and once
        // confirmed, open the phase-2 data connection under the same code, presenting this token.
        exchange.getResponseHeaders().set(SENDER_TOKEN_HEADER, sender.senderToken);
        respond(exchange, 200);
    }

    private static String generateToken() {
        byte[] bytes = new byte[18];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static long parseEstimatedBytes(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst(ESTIMATED_BYTES_HEADER);
        if (header == null) return 0;
        try {
            long value = Long.parseLong(header.trim());
            return value >= 0 && value <= MAX_ESTIMATED_BYTES ? value : -1;
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    /** Receiver: claim the code and learn its size estimate, without touching any terrain data. */
    private void handleReceiveInfo(HttpExchange exchange, String code) throws IOException {
        String remoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (!claimRateLimiter.allow(remoteAddress)) {
            respond(exchange, 429);
            return;
        }
        PendingSender sender = pending.get(code);
        if (sender == null) {
            respond(exchange, 404); // No one is currently sharing under this code.
            return;
        }
        if (!sender.claimedFlag.compareAndSet(false, true)) {
            respond(exchange, 409); // Another receiver already claimed this code.
            return;
        }
        sender.claimedAtNanos = System.nanoTime();
        sender.claimed.countDown();
        exchange.getResponseHeaders().set(ESTIMATED_BYTES_HEADER, Long.toString(sender.estimatedBytes));
        respond(exchange, 200);
    }

    /** Read-only peek at a code's current state - what the sender polls after "wait" returns claimed,
     * to show the receiver's decision live, without any of this blocking or mutating anything. */
    private void handleStatus(HttpExchange exchange, String code) throws IOException {
        PendingSender sender = pending.get(code);
        if (sender == null) {
            respond(exchange, 404);
            return;
        }
        String body = "claimed=" + sender.claimedFlag.get()
                + "\nconfirmed=" + sender.receiverConfirmed
                + "\ndeclined=" + sender.receiverDeclined
                + "\nestimatedBytes=" + sender.estimatedBytes;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** Receiver: record their confirm/decline decision. Declining frees the code immediately rather
     * than making the sender wait out its own timeout to find out. */
    private void handleReceiverDecision(HttpExchange exchange, String code, boolean confirmed) throws IOException {
        PendingSender sender = pending.get(code);
        if (sender == null || !sender.claimedFlag.get()) {
            respond(exchange, 404); // Nothing claimed under this code (expired, wrong order, ...).
            return;
        }
        if (confirmed) {
            sender.receiverConfirmed = true;
        } else {
            sender.receiverDeclined = true;
            pending.remove(code, sender);
        }
        respond(exchange, 200);
    }

    /** Sender phase 2: the actual data connection, only valid once the receiver has confirmed. */
    private void handleSendData(HttpExchange exchange, String code) throws IOException {
        PendingSender sender = pending.get(code);
        if (sender == null || !sender.claimedFlag.get() || !sender.receiverConfirmed) {
            respond(exchange, 404); // No confirmed receiver under this code (expired, wrong order, ...).
            return;
        }
        String presentedToken = exchange.getRequestHeaders().getFirst(SENDER_TOKEN_HEADER);
        if (presentedToken == null || !constantTimeEquals(presentedToken, sender.senderToken)) {
            respond(exchange, 403); // Doesn't have the token from this code's own phase-1 registration.
            return;
        }
        try {
            sender.dataExchange = exchange;
            sender.dataReady.countDown();
            // The receiver's handler thread is now reading exchange.getRequestBody() directly (see
            // handleReceiveData) and will count "finished" down once the transfer ends, one way or
            // another - this thread's only job until then is to keep the connection, and its request
            // body stream, alive. Bounded by TRANSFER_TIMEOUT_SECONDS so a receiver that vanishes after
            // confirming can't block this thread (and this pending entry) forever - see that constant.
            await(sender.finished, TRANSFER_TIMEOUT_SECONDS);
            respond(exchange, sender.transferSucceeded ? 200 : 502);
        } finally {
            pending.remove(code, sender);
        }
    }

    /** Receiver: the actual data fetch, only valid once this same receiver has confirmed. */
    private void handleReceiveData(HttpExchange exchange, String code) throws IOException {
        PendingSender sender = pending.get(code);
        if (sender == null) {
            respond(exchange, 404); // Nothing pending under this code.
            return;
        }
        if (!sender.claimedFlag.get() || !sender.receiverConfirmed) {
            respond(exchange, 409); // Haven't confirmed yet - not ready.
            return;
        }
        if (!sender.dataFetchStarted.compareAndSet(false, true)) {
            respond(exchange, 409); // The real receiver's own data fetch already claimed this code.
            return;
        }
        try {
            if (!await(sender.dataReady, DATA_CONNECT_TIMEOUT_SECONDS)) {
                // Unlike every other failure path here, phase 2 never showed up to remove this entry
                // itself (its finally block is what normally does that) - clean it up here instead, or
                // this code would stay stuck in "pending" forever and could never be reused.
                pending.remove(code, sender);
                respond(exchange, 502); // Sender never opened its data connection in time.
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, 0); // 0 = chunked, length unknown up front.
            sender.dataExchange.getRequestBody().transferTo(exchange.getResponseBody());
            sender.transferSucceeded = true;
        } catch (IOException exception) {
            System.err.println("[voxymap-sync-server] Relay transfer for code " + code + " failed: " + exception);
        } finally {
            sender.finished.countDown();
            exchange.close();
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean await(CountDownLatch latch, long timeoutSeconds) {
        try {
            return latch.await(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void respond(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
}

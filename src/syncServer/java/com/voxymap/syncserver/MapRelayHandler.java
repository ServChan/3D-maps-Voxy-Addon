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

final class MapRelayHandler implements HttpHandler {
    private static final Pattern CODE_PATTERN = Pattern.compile("[0-9]{6}");
    private static final String ESTIMATED_BYTES_HEADER = "X-VoxyMap-Estimated-Bytes";
    private static final String SENDER_TOKEN_HEADER = "X-VoxyMap-Sender-Token";
    private static final String RECEIVER_TOKEN_HEADER = "X-VoxyMap-Receiver-Token";
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private static final long SENDER_WAIT_TIMEOUT_SECONDS = 1800;

    private static final long DATA_CONNECT_TIMEOUT_SECONDS = 30;

    private static final long TRANSFER_TIMEOUT_SECONDS = 1800;

    private static final long CONFIRMATION_TIMEOUT_SECONDS = 120;
    private static final long REAP_INTERVAL_SECONDS = 30;
    private static final long MAX_ESTIMATED_BYTES = 1L << 40;
    private static final int CLAIM_ATTEMPTS_PER_MINUTE = 120;
    private static final int MAX_PENDING_SENDERS = 4096;

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
            boolean confirmationAbandoned = sender.claimedFlag.get() && !sender.receiverConfirmed && !sender.receiverDeclined
                    && now - sender.claimedAtNanos > TimeUnit.SECONDS.toNanos(CONFIRMATION_TIMEOUT_SECONDS);
            boolean uploadAbandoned = sender.receiverConfirmed && !sender.dataUploadStarted.get()
                    && now - sender.confirmedAtNanos > TimeUnit.SECONDS.toNanos(CONFIRMATION_TIMEOUT_SECONDS);
            if (confirmationAbandoned || uploadAbandoned) {
                pending.remove(entry.getKey(), sender);
            }
        }
    }

    private static final class PendingSender {
        final CountDownLatch claimed = new CountDownLatch(1);
        final CountDownLatch dataReady = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicBoolean claimedFlag = new AtomicBoolean(false);

        final AtomicBoolean dataFetchStarted = new AtomicBoolean(false);
        final AtomicBoolean dataUploadStarted = new AtomicBoolean(false);

        final String senderToken = generateToken();
        final String receiverToken = generateToken();
        volatile long estimatedBytes;
        volatile long claimedAtNanos;
        volatile long confirmedAtNanos;
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

            }
        }
    }

    private void route(HttpExchange exchange) throws IOException {

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

    private void handleSendWait(HttpExchange exchange, String code) throws IOException {
        PendingSender sender = new PendingSender();
        sender.estimatedBytes = parseEstimatedBytes(exchange);
        if (sender.estimatedBytes < 0) {
            respond(exchange, 400);
            return;
        }
        if (pending.size() >= MAX_PENDING_SENDERS) {
            respond(exchange, 503);
            return;
        }
        if (pending.putIfAbsent(code, sender) != null) {
            respond(exchange, 409);
            return;
        }
        if (!await(sender.claimed, SENDER_WAIT_TIMEOUT_SECONDS)) {
            pending.remove(code, sender);
            respond(exchange, 408);
            return;
        }

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

    private void handleReceiveInfo(HttpExchange exchange, String code) throws IOException {
        String remoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (!claimRateLimiter.allow(remoteAddress)) {
            respond(exchange, 429);
            return;
        }
        PendingSender sender = pending.get(code);
        if (sender == null) {
            respond(exchange, 404);
            return;
        }
        if (!sender.claimedFlag.compareAndSet(false, true)) {
            respond(exchange, 409);
            return;
        }
        sender.claimedAtNanos = System.nanoTime();
        sender.claimed.countDown();
        exchange.getResponseHeaders().set(ESTIMATED_BYTES_HEADER, Long.toString(sender.estimatedBytes));
        exchange.getResponseHeaders().set(RECEIVER_TOKEN_HEADER, sender.receiverToken);
        respond(exchange, 200);
    }

    private void handleStatus(HttpExchange exchange, String code) throws IOException {
        PendingSender sender = pending.get(code);
        if (sender == null) {
            respond(exchange, 404);
            return;
        }
        if (!presentsToken(exchange, SENDER_TOKEN_HEADER, sender.senderToken)) {
            respond(exchange, 403);
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

    private void handleReceiverDecision(HttpExchange exchange, String code, boolean confirmed) throws IOException {
        PendingSender sender = pending.get(code);
        if (sender == null || !sender.claimedFlag.get()) {
            respond(exchange, 404);
            return;
        }
        if (!presentsToken(exchange, RECEIVER_TOKEN_HEADER, sender.receiverToken)) {
            respond(exchange, 403);
            return;
        }
        if (confirmed) {
            sender.confirmedAtNanos = System.nanoTime();
            sender.receiverConfirmed = true;
        } else {
            sender.receiverDeclined = true;
            pending.remove(code, sender);
        }
        respond(exchange, 200);
    }

    private void handleSendData(HttpExchange exchange, String code) throws IOException {
        PendingSender sender = pending.get(code);
        if (sender == null || !sender.claimedFlag.get() || !sender.receiverConfirmed) {
            respond(exchange, 404);
            return;
        }
        if (!presentsToken(exchange, SENDER_TOKEN_HEADER, sender.senderToken)) {
            respond(exchange, 403);
            return;
        }
        if (!sender.dataUploadStarted.compareAndSet(false, true)) {
            respond(exchange, 409);
            return;
        }
        try {
            sender.dataExchange = exchange;
            sender.dataReady.countDown();

            await(sender.finished, TRANSFER_TIMEOUT_SECONDS);
            respond(exchange, sender.transferSucceeded ? 200 : 502);
        } finally {
            pending.remove(code, sender);
        }
    }

    private void handleReceiveData(HttpExchange exchange, String code) throws IOException {
        PendingSender sender = pending.get(code);
        if (sender == null) {
            respond(exchange, 404);
            return;
        }
        if (!presentsToken(exchange, RECEIVER_TOKEN_HEADER, sender.receiverToken)) {
            respond(exchange, 403);
            return;
        }
        if (!sender.claimedFlag.get() || !sender.receiverConfirmed) {
            respond(exchange, 409);
            return;
        }
        if (!sender.dataFetchStarted.compareAndSet(false, true)) {
            respond(exchange, 409);
            return;
        }
        try {
            if (!await(sender.dataReady, DATA_CONNECT_TIMEOUT_SECONDS)) {

                pending.remove(code, sender);
                respond(exchange, 502);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, 0);
            sender.dataExchange.getRequestBody().transferTo(exchange.getResponseBody());
            sender.transferSucceeded = true;
        } catch (IOException exception) {
            System.err.println("[voxymap-sync-server] Relay transfer for code " + code + " failed: " + exception);
        } finally {
            sender.finished.countDown();
            exchange.close();
        }
    }

    private static boolean presentsToken(HttpExchange exchange, String header, String expected) {
        String presented = exchange.getRequestHeaders().getFirst(header);
        return presented != null && constantTimeEquals(presented, expected);
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

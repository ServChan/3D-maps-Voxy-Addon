package com.voxymap.syncserver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapRelayHandlerTest {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    // A dedicated pool for the test's own background sender/receiver calls - the JVM-wide
    // ForkJoinPool.commonPool() that CompletableFuture.supplyAsync() uses by default is shared with
    // Gradle's own test-runner infrastructure, and under load a task can sit queued on it for seconds,
    // which previously made these tests flaky (a fixed short sleep isn't enough to bound that delay).
    private static final ExecutorService BACKGROUND = Executors.newCachedThreadPool();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private HttpServer startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/relay/", new MapRelayHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        this.server = server;
        return server;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Retries a GET until it's no longer a 404, instead of guessing a fixed delay - the other side
     * registering/claiming is an async, unbounded-latency event from the test's point of view. */
    private HttpResponse<byte[]> pollUntilNotFound(String path) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        HttpResponse<byte[]> response;
        do {
            response = CLIENT.send(
                    HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET().timeout(Duration.ofSeconds(5)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 404) {
                return response;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        return response;
    }

    private CompletableFuture<HttpResponse<Void>> sendWaitAsync(String code) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return CLIENT.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/" + code + "/wait"))
                                .header("X-VoxyMap-Estimated-Bytes", "12345")
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .timeout(Duration.ofSeconds(10))
                                .build(),
                        HttpResponse.BodyHandlers.discarding());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, BACKGROUND);
    }

    /** Drives one full sender+receiver pairing through wait -> info -> confirm -> data, returning both
     * sides' final responses. Used by tests that only care about the happy path having worked. */
    private record FullTransferResult(HttpResponse<Void> sendWaitResponse, HttpResponse<Void> sendDataResponse,
                                       HttpResponse<byte[]> receiveResponse, String senderToken) {
    }

    private FullTransferResult runFullTransfer(String code, byte[] payload) throws Exception {
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync(code);

        HttpResponse<byte[]> infoResponse = pollUntilNotFound("/relay/" + code + "/info");
        assertEquals(200, infoResponse.statusCode());

        HttpResponse<Void> confirmResponse = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/" + code + "/confirm"))
                        .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(200, confirmResponse.statusCode());

        HttpResponse<Void> waitResponse = waitFuture.get(10, TimeUnit.SECONDS);
        assertEquals(200, waitResponse.statusCode());
        String token = waitResponse.headers().firstValue("X-VoxyMap-Sender-Token").orElse(null);
        assertTrue(token != null && !token.isEmpty(), "sender token header missing");

        CompletableFuture<HttpResponse<Void>> sendDataFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return CLIENT.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/" + code))
                                .header("X-VoxyMap-Sender-Token", token)
                                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                                .timeout(Duration.ofSeconds(10))
                                .build(),
                        HttpResponse.BodyHandlers.discarding());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, BACKGROUND);

        HttpResponse<byte[]> receiveResponse = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/" + code)).GET().timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofByteArray());

        HttpResponse<Void> sendDataResponse = sendDataFuture.get(10, TimeUnit.SECONDS);
        return new FullTransferResult(waitResponse, sendDataResponse, receiveResponse, token);
    }

    @Test
    void fullHandshakeExchangesTheStreamLiveWithoutServerSideStorage() throws Exception {
        startServer();
        byte[] payload = "hello from the sender, this is explored terrain data".getBytes(StandardCharsets.UTF_8);

        FullTransferResult result = runFullTransfer("482910", payload);

        assertEquals(200, result.sendDataResponse().statusCode());
        assertEquals(200, result.receiveResponse().statusCode());
        assertEquals(new String(payload, StandardCharsets.UTF_8), new String(result.receiveResponse().body(), StandardCharsets.UTF_8));
    }

    @Test
    void infoReturnsTheEstimatedByteCountFromWait() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("222333");

        HttpResponse<byte[]> infoResponse = pollUntilNotFound("/relay/222333/info");
        assertEquals(200, infoResponse.statusCode());
        assertEquals("12345", infoResponse.headers().firstValue("X-VoxyMap-Estimated-Bytes").orElse(null));

        // Clean up: decline so the background wait future resolves instead of leaking past the test.
        CLIENT.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/222333/decline"))
                .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        waitFuture.get(10, TimeUnit.SECONDS);
    }

    @Test
    void statusReflectsClaimAndConfirmationLive() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("333444");

        // The phase-1 POST above is dispatched asynchronously - poll rather than assume it has already
        // registered with the server by the time this next statement runs.
        HttpResponse<byte[]> beforeClaim = pollUntilNotFound("/relay/333444/status");
        assertEquals(200, beforeClaim.statusCode());
        assertTrue(new String(beforeClaim.body(), StandardCharsets.UTF_8).contains("claimed=false"));

        pollUntilNotFound("/relay/333444/info");

        HttpResponse<byte[]> afterClaimBeforeConfirm = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/333444/status")).GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        String body = new String(afterClaimBeforeConfirm.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("claimed=true"), body);
        assertTrue(body.contains("confirmed=false"), body);

        CLIENT.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/333444/confirm"))
                        .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());

        HttpResponse<byte[]> afterConfirm = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/333444/status")).GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertTrue(new String(afterConfirm.body(), StandardCharsets.UTF_8).contains("confirmed=true"));

        String token = waitFuture.get(10, TimeUnit.SECONDS).headers().firstValue("X-VoxyMap-Sender-Token").orElse(null);
        assertTrue(token != null && !token.isEmpty());
    }

    @Test
    void decliningFreesTheCodeImmediatelyInsteadOfWaitingOutTheSenderTimeout() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("444555");
        pollUntilNotFound("/relay/444555/info");

        HttpResponse<Void> declineResponse = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/444555/decline"))
                        .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(200, declineResponse.statusCode());

        // The sender's wait doesn't get a special "declined" status of its own - it learns the outcome
        // by polling /status (which now 404s, since decline frees the code) rather than blocking
        // forever; this test just confirms the code is actually freed, immediately, not after 30 minutes.
        HttpResponse<byte[]> statusAfterDecline = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/444555/status")).GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(404, statusAfterDecline.statusCode());

        // A fresh sender can immediately reuse the code.
        CompletableFuture<HttpResponse<Void>> secondWaitFuture = sendWaitAsync("444555");
        Thread.sleep(100);
        HttpResponse<byte[]> secondInfo = pollUntilNotFound("/relay/444555/info");
        assertEquals(200, secondInfo.statusCode());
        CLIENT.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/444555/decline"))
                        .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        secondWaitFuture.get(10, TimeUnit.SECONDS);

        assertTrue(waitFuture.isDone() || waitFuture.get(10, TimeUnit.SECONDS) != null);
    }

    @Test
    void dataFetchWithoutConfirmingFirstIsRejected() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("555666");
        pollUntilNotFound("/relay/555666/info");

        HttpResponse<Void> prematureDataFetch = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/555666")).GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(409, prematureDataFetch.statusCode());

        CLIENT.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/555666/decline"))
                        .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        waitFuture.get(10, TimeUnit.SECONDS);
    }

    @Test
    void dataUploadWithWrongTokenIsRejected() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("666777");
        pollUntilNotFound("/relay/666777/info");
        CLIENT.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/666777/confirm"))
                        .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        waitFuture.get(10, TimeUnit.SECONDS);

        HttpResponse<Void> forgedDataUpload = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/666777"))
                        .header("X-VoxyMap-Sender-Token", "not-the-real-token")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1, 2, 3}))
                        .timeout(Duration.ofSeconds(5))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(403, forgedDataUpload.statusCode());
    }

    @Test
    void aSecondReceiverCannotClaimAnAlreadyClaimedCode() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("777888");
        pollUntilNotFound("/relay/777888/info");

        HttpResponse<Void> secondInfoAttempt = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/777888/info")).GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(409, secondInfoAttempt.statusCode());

        CLIENT.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/777888/decline"))
                        .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        waitFuture.get(10, TimeUnit.SECONDS);
    }

    @Test
    void aSecondSenderCannotRegisterTheSameCodeWhileOneIsPending() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> firstWait = sendWaitAsync("888999");
        Thread.sleep(200);

        HttpResponse<Void> secondWaitAttempt = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/888999/wait"))
                        .header("X-VoxyMap-Estimated-Bytes", "1")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(5))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(409, secondWaitAttempt.statusCode());

        CLIENT.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/888999/decline"))
                        .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        pollUntilNotFound("/relay/888999/info");
        firstWait.get(10, TimeUnit.SECONDS);
    }

    @Test
    void receivingWithNoSenderRegisteredReturns404() throws Exception {
        startServer();
        HttpResponse<Void> infoResponse = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/999111/info")).GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(404, infoResponse.statusCode());

        HttpResponse<Void> dataResponse = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/999111")).GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(404, dataResponse.statusCode());
    }

    @Test
    void malformedCodeIsRejected() throws Exception {
        startServer();
        HttpResponse<Void> response = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/abc/info")).GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(404, response.statusCode());
    }

    @Test
    void invalidOrImplausiblyLargeEstimateIsRejected() throws Exception {
        startServer();

        HttpResponse<Void> malformed = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/123123/wait"))
                        .header("X-VoxyMap-Estimated-Bytes", "not-a-number")
                        .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        HttpResponse<Void> enormous = CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/123124/wait"))
                        .header("X-VoxyMap-Estimated-Bytes", Long.toString((1L << 40) + 1))
                        .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());

        assertEquals(400, malformed.statusCode());
        assertEquals(400, enormous.statusCode());
    }
}

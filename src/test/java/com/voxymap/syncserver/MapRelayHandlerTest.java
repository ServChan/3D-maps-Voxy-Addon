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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapRelayHandlerTest {
    private static final String SENDER_TOKEN_HEADER = "X-VoxyMap-Sender-Token";
    private static final String RECEIVER_TOKEN_HEADER = "X-VoxyMap-Receiver-Token";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

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

    private String claim(String code) throws IOException, InterruptedException {
        HttpResponse<byte[]> infoResponse = pollUntilNotFound("/relay/" + code + "/info");
        assertEquals(200, infoResponse.statusCode());
        String token = infoResponse.headers().firstValue(RECEIVER_TOKEN_HEADER).orElse(null);
        assertNotNull(token, "receiver token header missing");
        return token;
    }

    private HttpResponse<Void> post(String path, String header, String token) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(5));
        if (token != null) {
            builder.header(header, token);
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.discarding());
    }

    private HttpResponse<byte[]> get(String path, String header, String token) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .GET().timeout(Duration.ofSeconds(10));
        if (token != null) {
            builder.header(header, token);
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<Void> decline(String code, String receiverToken) throws IOException, InterruptedException {
        return post("/relay/" + code + "/decline", RECEIVER_TOKEN_HEADER, receiverToken);
    }

    private HttpResponse<Void> confirm(String code, String receiverToken) throws IOException, InterruptedException {
        return post("/relay/" + code + "/confirm", RECEIVER_TOKEN_HEADER, receiverToken);
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

    private CompletableFuture<HttpResponse<Void>> sendDataAsync(String code, String senderToken, byte[] payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return CLIENT.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/relay/" + code))
                                .header(SENDER_TOKEN_HEADER, senderToken)
                                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                                .timeout(Duration.ofSeconds(10))
                                .build(),
                        HttpResponse.BodyHandlers.discarding());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, BACKGROUND);
    }

    private static String senderToken(HttpResponse<Void> waitResponse) {
        String token = waitResponse.headers().firstValue(SENDER_TOKEN_HEADER).orElse(null);
        assertTrue(token != null && !token.isEmpty(), "sender token header missing");
        return token;
    }

    @Test
    void fullHandshakeExchangesTheStreamLiveWithoutServerSideStorage() throws Exception {
        startServer();
        byte[] payload = "hello from the sender, this is explored terrain data".getBytes(StandardCharsets.UTF_8);
        String code = "482910";

        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync(code);
        String receiverToken = claim(code);
        assertEquals(200, confirm(code, receiverToken).statusCode());
        HttpResponse<Void> waitResponse = waitFuture.get(10, TimeUnit.SECONDS);
        assertEquals(200, waitResponse.statusCode());

        CompletableFuture<HttpResponse<Void>> sendDataFuture = sendDataAsync(code, senderToken(waitResponse), payload);
        HttpResponse<byte[]> receiveResponse = get("/relay/" + code, RECEIVER_TOKEN_HEADER, receiverToken);

        assertEquals(200, sendDataFuture.get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(200, receiveResponse.statusCode());
        assertEquals(new String(payload, StandardCharsets.UTF_8), new String(receiveResponse.body(), StandardCharsets.UTF_8));
    }

    @Test
    void infoReturnsTheEstimatedByteCountFromWait() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("222333");

        HttpResponse<byte[]> infoResponse = pollUntilNotFound("/relay/222333/info");
        assertEquals(200, infoResponse.statusCode());
        assertEquals("12345", infoResponse.headers().firstValue("X-VoxyMap-Estimated-Bytes").orElse(null));

        decline("222333", infoResponse.headers().firstValue(RECEIVER_TOKEN_HEADER).orElseThrow());
        waitFuture.get(10, TimeUnit.SECONDS);
    }

    @Test
    void statusReflectsClaimAndConfirmationLiveForTheSender() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("333444");
        String receiverToken = claim("333444");
        String senderToken = senderToken(waitFuture.get(10, TimeUnit.SECONDS));

        String beforeConfirm = new String(get("/relay/333444/status", SENDER_TOKEN_HEADER, senderToken).body(), StandardCharsets.UTF_8);
        assertTrue(beforeConfirm.contains("claimed=true"), beforeConfirm);
        assertTrue(beforeConfirm.contains("confirmed=false"), beforeConfirm);

        confirm("333444", receiverToken);

        String afterConfirm = new String(get("/relay/333444/status", SENDER_TOKEN_HEADER, senderToken).body(), StandardCharsets.UTF_8);
        assertTrue(afterConfirm.contains("confirmed=true"), afterConfirm);
    }

    @Test
    void statusWithoutTheSenderTokenIsRejected() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("334455");
        Thread.sleep(200);

        assertEquals(403, get("/relay/334455/status", SENDER_TOKEN_HEADER, null).statusCode());
        assertEquals(403, get("/relay/334455/status", SENDER_TOKEN_HEADER, "guessed").statusCode());

        decline("334455", claim("334455"));
        waitFuture.get(10, TimeUnit.SECONDS);
    }

    @Test
    void anotherClientCannotConfirmOrDownloadAClaimedTransfer() throws Exception {
        startServer();
        byte[] payload = "private terrain".getBytes(StandardCharsets.UTF_8);
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("445566");
        String receiverToken = claim("445566");

        assertEquals(403, confirm("445566", null).statusCode());
        assertEquals(403, confirm("445566", "forged").statusCode());
        assertEquals(403, decline("445566", "forged").statusCode());

        assertEquals(200, confirm("445566", receiverToken).statusCode());
        CompletableFuture<HttpResponse<Void>> sendDataFuture =
                sendDataAsync("445566", senderToken(waitFuture.get(10, TimeUnit.SECONDS)), payload);

        assertEquals(403, get("/relay/445566", RECEIVER_TOKEN_HEADER, null).statusCode());
        assertEquals(403, get("/relay/445566", RECEIVER_TOKEN_HEADER, "forged").statusCode());

        HttpResponse<byte[]> receiveResponse = get("/relay/445566", RECEIVER_TOKEN_HEADER, receiverToken);
        assertEquals(200, receiveResponse.statusCode());
        assertEquals("private terrain", new String(receiveResponse.body(), StandardCharsets.UTF_8));
        assertEquals(200, sendDataFuture.get(10, TimeUnit.SECONDS).statusCode());
    }

    @Test
    void decliningFreesTheCodeImmediatelyInsteadOfWaitingOutTheSenderTimeout() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("444555");
        String receiverToken = claim("444555");

        assertEquals(200, decline("444555", receiverToken).statusCode());
        String senderToken = senderToken(waitFuture.get(10, TimeUnit.SECONDS));
        assertEquals(404, get("/relay/444555/status", SENDER_TOKEN_HEADER, senderToken).statusCode());

        CompletableFuture<HttpResponse<Void>> secondWaitFuture = sendWaitAsync("444555");
        String secondReceiverToken = claim("444555");
        decline("444555", secondReceiverToken);
        assertEquals(200, secondWaitFuture.get(10, TimeUnit.SECONDS).statusCode());
    }

    @Test
    void dataFetchWithoutConfirmingFirstIsRejected() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("555666");
        String receiverToken = claim("555666");

        assertEquals(409, get("/relay/555666", RECEIVER_TOKEN_HEADER, receiverToken).statusCode());

        decline("555666", receiverToken);
        waitFuture.get(10, TimeUnit.SECONDS);
    }

    @Test
    void dataUploadWithWrongTokenIsRejected() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("666777");
        confirm("666777", claim("666777"));
        waitFuture.get(10, TimeUnit.SECONDS);

        HttpResponse<Void> forgedDataUpload = sendDataAsync("666777", "not-the-real-token", new byte[]{1, 2, 3})
                .get(10, TimeUnit.SECONDS);
        assertEquals(403, forgedDataUpload.statusCode());
    }

    @Test
    void aSecondReceiverCannotClaimAnAlreadyClaimedCode() throws Exception {
        startServer();
        CompletableFuture<HttpResponse<Void>> waitFuture = sendWaitAsync("777888");
        String receiverToken = claim("777888");

        assertEquals(409, get("/relay/777888/info", RECEIVER_TOKEN_HEADER, null).statusCode());

        decline("777888", receiverToken);
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

        decline("888999", claim("888999"));
        firstWait.get(10, TimeUnit.SECONDS);
    }

    @Test
    void receivingWithNoSenderRegisteredReturns404() throws Exception {
        startServer();
        assertEquals(404, get("/relay/999111/info", RECEIVER_TOKEN_HEADER, null).statusCode());
        assertEquals(404, get("/relay/999111", RECEIVER_TOKEN_HEADER, null).statusCode());
    }

    @Test
    void malformedCodeIsRejected() throws Exception {
        startServer();
        assertEquals(404, get("/relay/abc/info", RECEIVER_TOKEN_HEADER, null).statusCode());
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

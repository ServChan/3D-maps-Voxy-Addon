package com.voxymap.syncserver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointSyncServerTest {
    private static final String WORLD = "0123456789abcdef0123456789abcdef";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void getOnAnEmptyWorldReturnsAnEmptyList() throws Exception {
        server = WaypointSyncServer.start(0, "secret", tempDataDir());

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/waypoints/" + WORLD)).GET());

        assertEquals(200, response.statusCode());
        assertEquals("{\"waypoints\":[]}", response.body());
    }

    @Test
    void putWithoutAPasswordConfiguredIsAlwaysForbidden() throws Exception {
        server = WaypointSyncServer.start(0, null, tempDataDir());

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/waypoints/" + WORLD + "/id-1"))
                .header("X-VoxyMap-Password", "anything")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"name\":\"Base\",\"x\":1,\"y\":2,\"z\":3,\"color\":\"ff62e7ff\"}")));

        assertEquals(403, response.statusCode());
    }

    @Test
    void putWithoutTheCorrectPasswordIsRejected() throws Exception {
        server = WaypointSyncServer.start(0, "secret", tempDataDir());

        HttpResponse<String> wrongPassword = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/waypoints/" + WORLD + "/id-1"))
                .header("X-VoxyMap-Password", "wrong")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"name\":\"Base\",\"x\":1,\"y\":2,\"z\":3,\"color\":\"ff62e7ff\"}")));
        assertEquals(401, wrongPassword.statusCode());

        HttpResponse<String> noHeader = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/waypoints/" + WORLD + "/id-1"))
                .PUT(HttpRequest.BodyPublishers.ofString("{\"name\":\"Base\",\"x\":1,\"y\":2,\"z\":3,\"color\":\"ff62e7ff\"}")));
        assertEquals(401, noHeader.statusCode());
    }

    @Test
    void putWithTheCorrectPasswordThenGetRoundTripsTheWaypoint() throws Exception {
        server = WaypointSyncServer.start(0, "secret", tempDataDir());

        HttpResponse<String> put = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/waypoints/" + WORLD + "/id-1"))
                .header("X-VoxyMap-Password", "secret")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"name\":\"Дом\",\"x\":1,\"y\":2,\"z\":3,\"color\":\"ff62e7ff\"}", StandardCharsets.UTF_8)));
        assertEquals(200, put.statusCode());
        assertTrue(put.body().contains("\"Дом\""));

        HttpResponse<String> get = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/waypoints/" + WORLD)).GET());
        assertEquals(200, get.statusCode());
        assertTrue(get.body().contains("\"Дом\""));
        assertTrue(get.body().contains("\"id-1\""));
    }

    @Test
    void deleteWithTheCorrectPasswordRemovesTheWaypoint() throws Exception {
        server = WaypointSyncServer.start(0, "secret", tempDataDir());
        send(HttpRequest.newBuilder(URI.create(baseUrl() + "/waypoints/" + WORLD + "/id-1"))
                .header("X-VoxyMap-Password", "secret")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"name\":\"Base\",\"x\":1,\"y\":2,\"z\":3,\"color\":\"ff62e7ff\"}")));

        HttpResponse<String> delete = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/waypoints/" + WORLD + "/id-1"))
                .header("X-VoxyMap-Password", "secret")
                .DELETE());
        assertEquals(204, delete.statusCode());

        HttpResponse<String> get = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/waypoints/" + WORLD)).GET());
        assertEquals("{\"waypoints\":[]}", get.body());
    }

    @Test
    void malformedOrUnknownWorldIdIsRejected() throws Exception {
        server = WaypointSyncServer.start(0, "secret", tempDataDir());

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/waypoints/not-a-world-id")).GET());
        assertEquals(404, response.statusCode());
    }

    @Test
    void oversizedWaypointBodyIsRejectedBeforeJsonParsing() throws Exception {
        server = WaypointSyncServer.start(0, "secret", tempDataDir());
        String oversized = "x".repeat(64 * 1024 + 1);

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/waypoints/" + WORLD + "/id-1"))
                .header("X-VoxyMap-Password", "secret")
                .PUT(HttpRequest.BodyPublishers.ofString(oversized)));

        assertEquals(413, response.statusCode());
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return CLIENT.send(request.timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @TempDir
    Path tempDir;

    private Path tempDataDir() {
        return tempDir;
    }
}

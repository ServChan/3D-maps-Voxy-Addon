package com.voxymap.syncserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.voxymap.shared.WaypointDto;
import com.voxymap.shared.WaypointJson;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.Executors;

public final class WaypointSyncServer {
    private static final int MAX_WAYPOINT_BODY_BYTES = 64 * 1024;
    private static final int MAX_WAYPOINT_NAME_LENGTH = 256;

    private WaypointSyncServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = 8642;
        String bindAddress = "127.0.0.1";
        String password = null;
        Path dataDir = Path.of("voxymap-sync-data");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--bind" -> bindAddress = args[++i];
                case "--password" -> password = args[++i];
                case "--data" -> dataDir = Path.of(args[++i]);
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    System.err.println("Usage: java -jar voxymap-sync-server.jar [--bind 127.0.0.1] [--port 8642] [--password secret] [--data ./voxymap-sync-data]");
                    System.exit(1);
                    return;
                }
            }
        }

        HttpServer server = start(bindAddress, port, password, dataDir);

        System.out.println("[voxymap-sync-server] Listening on " + server.getAddress() + ", data dir " + dataDir.toAbsolutePath());
        System.out.println("[voxymap-sync-server] Write access is "
                + (password == null || password.isEmpty()
                        ? "DISABLED (read-only server - pass --password <secret> to enable adding/editing)"
                        : "ENABLED"));
    }

    static HttpServer start(int port, String password, Path dataDir) throws IOException {
        return start("127.0.0.1", port, password, dataDir);
    }

    static HttpServer start(String bindAddress, int port, String password, Path dataDir) throws IOException {
        WaypointStore store = new WaypointStore(dataDir);
        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 128);
        server.createContext("/waypoints/", new WaypointHandler(store, password));
        server.createContext("/relay/", new MapRelayHandler());
        server.setExecutor(Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("voxymap-sync-server-", 0).factory()));
        server.start();
        return server;
    }

    private record WaypointHandler(WaypointStore store, String password) implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                route(exchange);
            } catch (RequestTooLargeException exception) {
                respond(exchange, 413, "");
            } catch (RuntimeException exception) {
                System.err.println("[voxymap-sync-server] Unhandled error handling " + exchange.getRequestURI() + ": " + exception);
                respond(exchange, 500, "");
            } finally {
                exchange.close();
            }
        }

        private void route(HttpExchange exchange) throws IOException {

            String[] segments = exchange.getRequestURI().getPath().split("/");
            String worldId = segments.length > 2 ? segments[2] : null;
            String id = segments.length > 3 ? segments[3] : null;
            if (worldId == null || !WaypointStore.isValidWorldId(worldId) || segments.length > 4) {
                respond(exchange, 404, "");
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equals(method) && id == null) {
                handleGet(exchange, worldId);
            } else if ("PUT".equals(method) && id != null) {
                handlePut(exchange, worldId, id);
            } else if ("DELETE".equals(method) && id != null) {
                handleDelete(exchange, worldId, id);
            } else {
                respond(exchange, 405, "");
            }
        }

        private void handleGet(HttpExchange exchange, String worldId) throws IOException {
            List<WaypointDto> waypoints = store.list(worldId);
            respondJson(exchange, 200, WaypointJson.writeList(waypoints));
        }

        private void handlePut(HttpExchange exchange, String worldId, String id) throws IOException {
            if (!authorized(exchange)) {
                respond(exchange, password == null || password.isEmpty() ? 403 : 401, "");
                return;
            }
            WaypointDto submitted = WaypointJson.readOne(readBody(exchange), id);
            if (submitted == null || submitted.name().length() > MAX_WAYPOINT_NAME_LENGTH) {
                respond(exchange, 400, "");
                return;
            }

            WaypointDto saved = store.put(worldId, new WaypointDto(id, submitted.name(), submitted.x(), submitted.y(), submitted.z(), submitted.color()));
            respondJson(exchange, 200, WaypointJson.writeOne(saved));
        }

        private void handleDelete(HttpExchange exchange, String worldId, String id) throws IOException {
            if (!authorized(exchange)) {
                respond(exchange, password == null || password.isEmpty() ? 403 : 401, "");
                return;
            }
            respond(exchange, store.delete(worldId, id) ? 204 : 404, "");
        }

        private boolean authorized(HttpExchange exchange) {
            if (password == null || password.isEmpty()) return false;
            String presented = exchange.getRequestHeaders().getFirst("X-VoxyMap-Password");
            if (presented == null) return false;
            return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
        }

        private static String readBody(HttpExchange exchange) throws IOException {
            try (InputStream input = exchange.getRequestBody()) {
                byte[] bytes = input.readNBytes(MAX_WAYPOINT_BODY_BYTES + 1);
                if (bytes.length > MAX_WAYPOINT_BODY_BYTES) {
                    throw new RequestTooLargeException();
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }

        private static final class RequestTooLargeException extends IOException {
        }

        private static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            respond(exchange, status, body);
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
        }
    }
}

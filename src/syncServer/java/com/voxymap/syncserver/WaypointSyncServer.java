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
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Standalone HTTP server backing two VoxyMap features. Plain Java, no Minecraft/Fabric dependency -
 * run it anywhere with
 * {@code java -jar voxymap-sync-server.jar [--port 8642] [--password <secret>] [--data <dir>]}.
 *
 * <p>Waypoint sync (JSON bodies, UTF-8 - see {@link WaypointJson}):
 * <pre>
 *   GET    /waypoints/{worldId}       -> 200 {"waypoints":[{id,name,x,y,z,color}, ...]}, no auth needed
 *   PUT    /waypoints/{worldId}/{id}  -> create/replace one waypoint, needs X-VoxyMap-Password
 *   DELETE /waypoints/{worldId}/{id}  -> delete one waypoint, needs X-VoxyMap-Password
 * </pre>
 *
 * <p>Explored-terrain relay - a live byte pipe between two players, nothing stored server-side (see
 * {@link MapRelayHandler}):
 * <pre>
 *   POST /relay/{code}  -> sender, blocks until a receiver claims the code, then streams to them
 *   GET  /relay/{code}  -> receiver, claims a waiting sender and streams its data live
 * </pre>
 *
 * <p>{@code worldId} is VoxyMap's 32 lowercase-hex Voxy world id. There is exactly one password for
 * the whole server (not a per-user account system): knowing just the address gets read access to
 * waypoints, knowing the address and the password gets read/write access; the terrain relay needs no
 * password at all since a code is only known to whoever the sender shared it with. Traffic is plain
 * HTTP - deliberately not encrypted, since this is meant for a trusted LAN or a friend group, not a
 * public deployment. Put it behind a reverse proxy with TLS if it needs to be internet-facing.
 */
public final class WaypointSyncServer {
    private WaypointSyncServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = 8642;
        String password = null;
        Path dataDir = Path.of("voxymap-sync-data");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--password" -> password = args[++i];
                case "--data" -> dataDir = Path.of(args[++i]);
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    System.err.println("Usage: java -jar voxymap-sync-server.jar [--port 8642] [--password secret] [--data ./voxymap-sync-data]");
                    System.exit(1);
                    return;
                }
            }
        }

        HttpServer server = start(port, password, dataDir);

        System.out.println("[voxymap-sync-server] Listening on port " + server.getAddress().getPort() + ", data dir " + dataDir.toAbsolutePath());
        System.out.println("[voxymap-sync-server] Write access is "
                + (password == null || password.isEmpty()
                        ? "DISABLED (read-only server - pass --password <secret> to enable adding/editing)"
                        : "ENABLED"));
    }

    /** Package-private so tests can start a server on an ephemeral port (0) and stop it afterward. */
    static HttpServer start(int port, String password, Path dataDir) throws IOException {
        WaypointStore store = new WaypointStore(dataDir);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/waypoints/", new WaypointHandler(store, password));
        server.createContext("/relay/", new MapRelayHandler());
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "voxymap-sync-server");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        return server;
    }

    private record WaypointHandler(WaypointStore store, String password) implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                route(exchange);
            } catch (RuntimeException exception) {
                System.err.println("[voxymap-sync-server] Unhandled error handling " + exchange.getRequestURI() + ": " + exception);
                respond(exchange, 500, "");
            } finally {
                exchange.close();
            }
        }

        private void route(HttpExchange exchange) throws IOException {
            // Path is "/waypoints/{worldId}" or "/waypoints/{worldId}/{id}" - segments[0] is the
            // empty string before the leading slash, segments[1] is "waypoints".
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
            if (submitted == null) {
                respond(exchange, 400, "");
                return;
            }
            // The id always comes from the URL, never from the body, so a client can't be tricked
            // (or accidentally cause itself) to overwrite a different waypoint than the one it targeted.
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
            return password.equals(exchange.getRequestHeaders().getFirst("X-VoxyMap-Password"));
        }

        private static String readBody(HttpExchange exchange) throws IOException {
            try (InputStream input = exchange.getRequestBody()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
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

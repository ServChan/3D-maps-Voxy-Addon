package com.voxymap.shared;

/**
 * Wire representation of one waypoint, shared verbatim by the client mod's sync HTTP client and the
 * standalone {@code voxymap-sync-server} module - both sides need to agree on this shape byte-for-byte,
 * so it lives here rather than being duplicated. Deliberately has no Minecraft/Fabric dependency.
 */
public record WaypointDto(String id, String name, double x, double y, double z, int color) {
}

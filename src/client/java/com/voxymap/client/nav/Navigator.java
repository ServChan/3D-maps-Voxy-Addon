package com.voxymap.client.nav;

import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.map.MinimapRenderer;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class Navigator {
    static final double ARRIVE_BLOCKS = 6.0;
    static final double OFF_ROUTE_BLOCKS = 16.0;
    static final double OFF_FLIGHT_BLOCKS = 96.0;
    static final int OFF_ROUTE_TICKS = 40;
    static final long REPLAN_COOLDOWN_NANOS = 8_000_000_000L;
    static final int CAPABILITY_CHECK_TICKS = 40;

    public record Target(String name, double x, double y, double z, String worldId) {
    }

    public enum Status { IDLE, PLANNING, ACTIVE }

    private record Result(long generation, Route ground, Route flight, Capabilities caps, boolean groundFound,
                          Throwable error) {
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VoxyMap route");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY + 1);
        return thread;
    });
    private static final AtomicLong GENERATION = new AtomicLong();

    private static volatile Result pending;
    private static Target target;
    private static Route route;
    private static Route alternative;
    private static Capabilities planCaps = Capabilities.ON_FOOT;
    private static Status status = Status.IDLE;
    private static RouteProgress.State progress;
    private static int segmentHint;
    private static int offRouteTicks;
    private static int capabilityTicks;
    private static long lastPlanNanos;
    private static boolean announced;
    private static boolean groundMissing;

    private Navigator() {
    }

    public static void start(Minecraft minecraft, Target destination) {
        if (minecraft.level == null || minecraft.player == null || destination == null) {
            return;
        }
        stopQuietly();
        target = destination;
        announced = false;
        plan(minecraft);
    }

    public static void stop(Minecraft minecraft) {
        boolean wasActive = target != null;
        stopQuietly();
        if (wasActive && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable("message.voxymap.route.stopped"));
        }
    }

    private static void stopQuietly() {
        GENERATION.incrementAndGet();
        pending = null;
        target = null;
        route = null;
        alternative = null;
        progress = null;
        status = Status.IDLE;
        segmentHint = 0;
        offRouteTicks = 0;
        groundMissing = false;
    }

    public static boolean active() {
        return target != null;
    }

    public static Target target() {
        return target;
    }

    public static Route route() {
        return route;
    }

    public static Route alternative() {
        return alternative;
    }

    public static Status status() {
        return status;
    }

    public static RouteProgress.State progress() {
        return progress;
    }

    public static Capabilities capabilities() {
        return planCaps;
    }

    public static boolean groundMissing() {
        return groundMissing;
    }

    public static void tick(Minecraft minecraft) {
        if (target == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null) {
            stopQuietly();
            return;
        }
        if (!target.worldId().equals(MinimapRenderer.currentWorldId(minecraft))) {
            stopQuietly();
            player.sendSystemMessage(Component.translatable("message.voxymap.route.other_dimension"));
            return;
        }
        Result result = pending;
        if (result != null && result.generation() == GENERATION.get()) {
            pending = null;
            apply(minecraft, result);
        }
        if (Math.hypot(target.x() - player.getX(), target.z() - player.getZ()) <= ARRIVE_BLOCKS) {
            String name = target.name();
            stopQuietly();
            player.sendSystemMessage(Component.translatable("message.voxymap.route.arrived", name));
            return;
        }
        if (route == null) {
            return;
        }
        progress = RouteProgress.locate(route, player.getX(), player.getZ(), segmentHint);
        segmentHint = progress.segment();
        boolean replanning = status == Status.PLANNING;
        double limit = route.flight() ? OFF_FLIGHT_BLOCKS : OFF_ROUTE_BLOCKS;
        offRouteTicks = !route.direct() && progress.offRoute() > limit ? offRouteTicks + 1 : 0;
        boolean cooled = System.nanoTime() - lastPlanNanos > REPLAN_COOLDOWN_NANOS;
        if (!replanning && cooled && offRouteTicks > OFF_ROUTE_TICKS) {
            offRouteTicks = 0;
            plan(minecraft);
            return;
        }
        if (++capabilityTicks >= CAPABILITY_CHECK_TICKS) {
            capabilityTicks = 0;
            Capabilities now = capabilities(player);
            if (!replanning && cooled && (now.boat() != planCaps.boat() || now.canFly() != planCaps.canFly())) {
                plan(minecraft);
            }
        }
    }

    private static void apply(Minecraft minecraft, Result result) {
        status = Status.ACTIVE;
        planCaps = result.caps();
        if (result.error() != null) {
            VoxyMapClient.LOGGER.warn("Route planning failed, showing the direct line", result.error());
        }
        groundMissing = !result.groundFound();
        if (ElytraPlanner.prefer(result.flight(), result.ground(), result.caps())) {
            route = result.flight();
            alternative = result.ground() != null && !result.ground().direct() ? result.ground() : null;
        } else {
            route = result.ground();
            alternative = null;
        }
        segmentHint = 0;
        offRouteTicks = 0;
        if (!announced && route != null && minecraft.player != null) {
            announced = true;
            minecraft.player.sendSystemMessage(Component.translatable("message.voxymap.route.started", target.name(),
                    RouteText.distance(route.length()), RouteText.duration(route.seconds()),
                    RouteText.mode(route.points().getFirst().mode())));
            if (groundMissing && !route.flight()) {
                minecraft.player.sendSystemMessage(Component.translatable("message.voxymap.route.direct"));
            }
        }
    }

    private static void plan(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null || target == null) {
            return;
        }
        long generation = GENERATION.incrementAndGet();
        pending = null;
        status = Status.PLANNING;
        lastPlanNanos = System.nanoTime();
        Capabilities caps = capabilities(player);
        double startX = player.getX();
        double startY = player.getY();
        double startZ = player.getZ();
        Target goal = target;
        NavArea area = NavArea.around(startX, startZ, goal.x(), goal.z());
        WorldEngine engine = WorldIdentifier.ofEngineNullable(level);
        int minY = level.getMinY();
        int maxY = level.getMinY() + level.getHeight();
        boolean ceiling = level.dimensionType().hasCeiling();
        BooleanSupplier cancelled = () -> GENERATION.get() != generation;
        WORKER.execute(() -> {
            if (cancelled.getAsBoolean()) {
                return;
            }
            Route ground = null;
            Route flight = null;
            Throwable error = null;
            try {
                NavGrid grid = engine != null && area != null
                        ? NavTerrain.scan(new NavTerrain.Request(engine, area, minY, maxY, ceiling, (int) Math.floor(startY)), cancelled)
                        : null;
                if (cancelled.getAsBoolean()) {
                    return;
                }
                if (grid != null && NavTerrain.anyKnown(grid)) {
                    ground = RoutePlanner.plan(grid, caps, startX, startY, startZ, goal.x(), goal.y(), goal.z(), cancelled);
                }
                if (cancelled.getAsBoolean()) {
                    return;
                }
                if (caps.elytra() && !ceiling) {
                    NavGrid heights = grid != null ? grid : NavGrid.empty(0, 0, 1, 1, 1);
                    flight = ElytraPlanner.plan(heights, startX, startY, startZ, goal.x(), goal.y(), goal.z(), maxY);
                }
            } catch (RuntimeException | LinkageError failure) {
                error = failure;
            }
            boolean found = ground != null;
            if (ground == null) {
                double goalY = Double.isNaN(goal.y()) ? startY : goal.y();
                ground = Route.direct(startX, startY, startZ, goal.x(), goalY, goal.z());
            }
            pending = new Result(generation, ground, flight, caps, found, error);
        });
    }

    public static Capabilities capabilities(LocalPlayer player) {
        boolean boat = player.getVehicle() instanceof AbstractBoat;
        boolean elytraWorn = player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER);
        boolean elytra = elytraWorn;
        int rockets = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ItemTags.BOATS) || stack.is(ItemTags.CHEST_BOATS)) {
                boat = true;
            } else if (stack.has(DataComponents.GLIDER)) {
                elytra = true;
            } else if (stack.is(Items.FIREWORK_ROCKET)) {
                rockets += stack.getCount();
            }
        }
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (offhand.is(Items.FIREWORK_ROCKET) && inventory.getContainerSize() <= 36) {
            rockets += offhand.getCount();
        }
        return new Capabilities(boat, elytra, elytraWorn, rockets);
    }
}

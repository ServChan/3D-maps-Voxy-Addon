package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomCutawayComputerTest {
    @Test
    void smallEnclosedRoomIsDetectedAndCeilingMatchesItsRoof() {
        // A 3x3 floor at y=0..2 (player height) inside solid rock everywhere else.
        RoomCutawayComputer.Result result = RoomCutawayComputer.compute(0, 0, 0, (x, y, z) ->
                passable(x >= -1 && x <= 1 && z >= -1 && z <= 1 && y >= 0 && y <= 2));

        assertTrue(result.enclosed);
        assertEquals(2, result.ceilingAt(0, 0));
        assertEquals(2, result.ceilingAt(1, -1));
    }

    @Test
    void openWorldNeverTerminatesWithinTheSearchWindowSoNothingIsCut() {
        RoomCutawayComputer.Result result = RoomCutawayComputer.compute(0, 64, 0,
                (x, y, z) -> RoomCutawayComputer.PASSABLE);

        assertFalse(result.enclosed);
        assertEquals(RoomCutawayComputer.NO_CEILING, result.ceilingAt(0, 0));
    }

    @Test
    void verifiedPartialModeKeepsOnlyReachableAirWhenRemoteDataEnds() {
        RoomCutawayComputer.Search search = RoomCutawayComputer.startVerifiedPartial(0, 64, 0);
        while (!search.isComplete()) {
            search.advance((x, y, z) -> {
                if (x >= -3 && x <= 3 && z >= -3 && z <= 3 && y >= 64 && y <= 68) {
                    return RoomCutawayComputer.PASSABLE;
                }
                return x == 4 ? RoomCutawayComputer.UNKNOWN : RoomCutawayComputer.BLOCKED;
            }, Integer.MAX_VALUE);
        }

        RoomCutawayComputer.Result result = search.result();
        assertFalse(result.enclosed);
        assertTrue(result.hasCutaway());
        assertEquals(68, result.ceilingAt(0, 0));
        assertEquals(RoomCutawayComputer.NO_CEILING, result.ceilingAt(5, 0));
    }

    @Test
    void caveOnTheOtherSideOfAWallIsNotTreatedAsPartOfTheRoom() {
        // Player's room: x in [-2,-1]. A solid wall at x=0. A second, disconnected room at x in [1,2].
        RoomCutawayComputer.Result result = RoomCutawayComputer.compute(-1, 0, 0, (x, y, z) ->
                passable((x == -2 || x == -1 || x == 1 || x == 2) && y >= 0 && y <= 1 && z == 0));

        assertTrue(result.enclosed);
        assertEquals(1, result.ceilingAt(-1, 0));
        // The far side of the wall was never reached, so it must not get a ceiling (and thus not be cut).
        assertEquals(RoomCutawayComputer.NO_CEILING, result.ceilingAt(1, 0));
        assertEquals(RoomCutawayComputer.NO_CEILING, result.ceilingAt(2, 0));
    }

    @Test
    void surfaceBehindSeveralBlocksOfRockIsNeverReachedOrCut() {
        // A single sealed pocket (just the player's feet+head cell) around the player; everything
        // else, including any "surface" blocks further out, is solid and therefore unreachable.
        RoomCutawayComputer.Result result = RoomCutawayComputer.compute(5, 10, 5, (x, y, z) ->
                passable(x == 5 && z == 5 && (y == 10 || y == 11)));

        assertTrue(result.enclosed);
        assertEquals(11, result.ceilingAt(5, 5));
        assertEquals(RoomCutawayComputer.NO_CEILING, result.ceilingAt(20, 20));
    }

    @Test
    void massiveCavernExceedingTheCellBudgetKeepsTheVerifiedCeilingNearThePlayer() {
        // The cavern extends well past both HORIZONTAL_RADIUS and the MAX_CELLS budget, so it can
        // never fully enclose - but the real ceiling only 20 blocks straight up is confirmed well
        // before the budget runs out. Hitting a scope/cost cap never touches unverified data, so that
        // confirmed ceiling should still show instead of the whole result vanishing to "nothing cut"
        // just because the far reaches of the cavern were never (and could never be) fully explored.
        RoomCutawayComputer.Result result = RoomCutawayComputer.compute(0, 0, 0, (x, y, z) ->
                passable(Math.abs(x) <= 40 && Math.abs(y) <= 20 && Math.abs(z) <= 40));

        assertFalse(result.enclosed);
        assertTrue(result.hasCutaway());
        assertEquals(20, result.ceilingAt(0, 0));
    }

    @Test
    void aCorridorThatExtendsPastTheSearchRadiusStillKeepsAVerifiedCeilingNearThePlayer() {
        // A 3-wide, 2-tall corridor with a floor, ceiling and west wall, open forever to the east -
        // it can never fully enclose (it never terminates), but the floor and ceiling right where the
        // player stands were verified beyond doubt, so that part of the result should survive instead
        // of the whole cutaway vanishing just because the corridor runs past the search radius.
        RoomCutawayComputer.Result result = RoomCutawayComputer.compute(0, 0, 0, (x, y, z) ->
                passable(x >= -1 && z >= -1 && z <= 1 && y >= 0 && y <= 1));

        assertFalse(result.enclosed);
        assertTrue(result.hasCutaway());
        assertEquals(1, result.ceilingAt(0, 0));
    }

    @Test
    void ceilingHeightIsTheHighestReachableCellNotTheGlobalPlayerHeightOffset() {
        // Room A around the player, ceiling at y=3. A taller, separate room B far away must not
        // influence room A's ceiling even though it is technically within the search radius.
        Set<Long> roomA = new HashSet<>();
        for (int y = 0; y <= 3; y++) {
            roomA.add(packed(0, y, 0));
        }
        Set<Long> roomB = new HashSet<>();
        for (int y = 0; y <= 20; y++) {
            roomB.add(packed(40, y, 40));
        }

        RoomCutawayComputer.Result result = RoomCutawayComputer.compute(0, 0, 0,
                (x, y, z) -> passable(roomA.contains(packed(x, y, z))));

        assertTrue(result.enclosed);
        assertEquals(3, result.ceilingAt(0, 0));
        assertFalse(roomB.isEmpty());
    }

    @Test
    void unknownDataLikeAnUnloadedChunkOnAServerNeverGetsTreatedAsOpenAirLeakingTheFill() {
        // The room is genuinely small and enclosed, but one of its walls borders an area the
        // client has no reliable data for (e.g. a chunk the server hasn't sent, or obfuscated
        // anti-xray data). That must never be silently treated as passable air: since we can't
        // certify what's beyond it, the whole result must fall back to OPEN (no cut) rather than
        // guess and possibly cut too much or too little.
        RoomCutawayComputer.Result result = RoomCutawayComputer.compute(0, 0, 0, (x, y, z) -> {
            if (x >= -1 && x <= 1 && z >= -1 && z <= 1 && y >= 0 && y <= 2) {
                return RoomCutawayComputer.PASSABLE;
            }
            if (x == 2 && z == 0 && y == 1) {
                return RoomCutawayComputer.UNKNOWN;
            }
            return RoomCutawayComputer.BLOCKED;
        });

        assertFalse(result.enclosed);
    }

    @Test
    void unknownDataAtThePlayersOwnFeetNeverSeedsTheFillAsOpenAir() {
        // The player's reported position sits in what would otherwise be a small enclosed room, but
        // the client has no reliable data for that exact cell (e.g. a teleport landed just ahead of
        // the chunk data, or a clipped/stale position). The seed cell must go through the same
        // classifier as every other cell: it must not be silently assumed passable, or an unverified
        // starting point could still expand into a "confirmed" cutaway.
        RoomCutawayComputer.Result result = RoomCutawayComputer.compute(0, 0, 0, (x, y, z) -> {
            if (x == 0 && y == 0 && z == 0) {
                return RoomCutawayComputer.UNKNOWN;
            }
            return passable(x >= -1 && x <= 1 && z >= -1 && z <= 1 && y >= 0 && y <= 2);
        });

        assertFalse(result.hasCutaway());
        assertFalse(result.enclosed);
    }

    @Test
    void blockedHeadroomAboveThePlayerIsARoofNotABoundaryHit() {
        // The cell directly above the player's feet (the "headroom" seed) is solid - a perfectly
        // normal low ceiling - and must not be treated as an unverified boundary. The room is still
        // enclosed and its ceiling is the floor the player is standing on.
        RoomCutawayComputer.Result result = RoomCutawayComputer.compute(0, 0, 0, (x, y, z) ->
                passable(x >= -1 && x <= 1 && z >= -1 && z <= 1 && y == 0));

        assertTrue(result.enclosed);
        assertEquals(0, result.ceilingAt(0, 0));
    }

    @Test
    void incrementalSearchHonorsItsPerTickNodeBudget() {
        RoomCutawayComputer.Search search = RoomCutawayComputer.start(0, 0, 0);

        assertFalse(search.advance((x, y, z) -> passable(
                x >= -4 && x <= 4 && y >= 0 && y <= 3 && z >= -4 && z <= 4), 1));
        assertFalse(search.isComplete());

        while (!search.advance((x, y, z) -> passable(
                x >= -4 && x <= 4 && y >= 0 && y <= 3 && z >= -4 && z <= 4), 16)) {
            // Continue in small, bounded slices like the screen tick does.
        }
        assertTrue(search.result().enclosed);
    }

    private static int passable(boolean value) {
        return value ? RoomCutawayComputer.PASSABLE : RoomCutawayComputer.BLOCKED;
    }

    private static long packed(int x, int y, int z) {
        return ((long) x << 32) ^ ((long) y << 16) ^ (long) (z & 0xFFFF);
    }
}

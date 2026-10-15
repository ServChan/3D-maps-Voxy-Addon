package com.voxymap.client.map;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class SpriteReadLock {
    public static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    private SpriteReadLock() {
    }
}

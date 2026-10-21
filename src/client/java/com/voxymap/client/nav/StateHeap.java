package com.voxymap.client.nav;

import java.util.Arrays;

final class StateHeap {
    private int[] states;
    private float[] keys;
    private int size;

    StateHeap(int capacity) {
        states = new int[Math.max(16, capacity)];
        keys = new float[states.length];
    }

    boolean isEmpty() {
        return size == 0;
    }

    float peekKey() {
        return keys[0];
    }

    void push(int state, float key) {
        if (size == states.length) {
            states = Arrays.copyOf(states, size * 2);
            keys = Arrays.copyOf(keys, size * 2);
        }
        int at = size++;
        while (at > 0) {
            int up = (at - 1) >>> 1;
            if (keys[up] <= key) {
                break;
            }
            states[at] = states[up];
            keys[at] = keys[up];
            at = up;
        }
        states[at] = state;
        keys[at] = key;
    }

    int pop() {
        int top = states[0];
        size--;
        if (size == 0) {
            return top;
        }
        int lastState = states[size];
        float lastKey = keys[size];
        int at = 0;
        while (true) {
            int child = at * 2 + 1;
            if (child >= size) {
                break;
            }
            if (child + 1 < size && keys[child + 1] < keys[child]) {
                child++;
            }
            if (keys[child] >= lastKey) {
                break;
            }
            states[at] = states[child];
            keys[at] = keys[child];
            at = child;
        }
        states[at] = lastState;
        keys[at] = lastKey;
        return top;
    }
}

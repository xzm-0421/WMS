package com.wms.mes.service;

import com.wms.common.exception.BusinessException;
import com.wms.mes.MesProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MesProcessLockTest {

    private MesProcessLock newLock(long waitSeconds) {
        MesProperties props = new MesProperties();
        props.setLockMode("LOCAL");
        props.setProcessLockWaitSeconds(waitSeconds);
        return new MesProcessLock(props);
    }

    private void awaitRelease(CountDownLatch release) {
        try {
            release.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void localModeReturnsActionResult() {
        MesProcessLock lock = newLock(5);
        try {
            String result = lock.withLock("MO001", "P01", () -> "ok");
            assertEquals("ok", result);
        } finally {
            lock.shutdown();
        }
    }

    @Test
    void busyWhenSameKeyLockedAndWaitTimeout() throws Exception {
        MesProcessLock lock = newLock(0);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> holderError = new AtomicReference<>();

        Thread holder = new Thread(() -> {
            try {
                lock.withLock("MO001", "P01", () -> {
                    acquired.countDown();
                    awaitRelease(release);
                    return null;
                });
            } catch (Throwable t) {
                holderError.set(t);
            }
        });
        holder.start();
        assertTrue(acquired.await(5, TimeUnit.SECONDS));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lock.withLock("MO001", "P01", () -> "should-not-run"));
        assertEquals("PROCESS_BUSY", ex.getErrorType());

        release.countDown();
        holder.join(5000);
        assertNull(holderError.get());
        lock.shutdown();
    }

    @Test
    void differentKeysDoNotBlock() throws Exception {
        MesProcessLock lock = newLock(0);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> lock.withLock("MO001", "P01", () -> {
            acquired.countDown();
            awaitRelease(release);
            return null;
        }));
        holder.start();
        assertTrue(acquired.await(5, TimeUnit.SECONDS));

        String other = lock.withLock("MO001", "P02", () -> "ok");
        assertEquals("ok", other);

        release.countDown();
        holder.join(5000);
        lock.shutdown();
    }
}

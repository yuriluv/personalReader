package com.foobnix.sys;

import org.junit.Test;
import org.junit.Before;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Tests for AsyncDocLoader — the async document opening and page counting orchestrator.
 *
 * Behavior under test:
 * 1. openDocument returns immediately with pageCount=0, isCounting=true
 * 2. getPageCount completes on background thread and fires callback with the real count
 * 3. Cancellation discards the result of a running page count
 * 4. Opening a new document while counting cancels the previous count
 */
public class AsyncDocLoaderTest {

    private ExecutorService executor;

    @Before
    public void setUp() {
        executor = Executors.newSingleThreadExecutor();
    }

    // --- Behavior 1: openDocument returns immediately with pageCount=0, isCounting=true ---

    @Test
    public void openDocument_returnsImmediatelyWithZeroPageCount() {
        FakeCountableDocument slowDoc = new FakeCountableDocument(42, 5000L);
        AsyncDocLoader loader = new AsyncDocLoader(slowDoc, executor);

        AsyncDocLoader.Result result = loader.openDocument();

        // Must return immediately — pageCount should be 0 while counting
        assertNotNull(result.document);
        assertEquals(0, result.initialPageCount);
        assertTrue(result.document.isCounting());
    }

    // --- Behavior 2: callback fires with real page count after background counting ---

    @Test
    public void pageCountCallback_firesWithRealCount() throws InterruptedException {
        FakeCountableDocument fastDoc = new FakeCountableDocument(42, 100L);
        AsyncDocLoader loader = new AsyncDocLoader(fastDoc, executor);
        AtomicInteger callbackCount = new AtomicInteger(0);
        AtomicInteger resultPages = new AtomicInteger(0);

        loader.openDocument();
        loader.setOnPageCountListener((count) -> {
            callbackCount.incrementAndGet();
            resultPages.set(count);
        });

        // Wait for callback
        Thread.sleep(500);

        assertEquals(1, callbackCount.get());
        assertEquals(42, resultPages.get());
        assertFalse(fastDoc.isCounting());
    }

    // --- Behavior 3: cancellation discards the result ---

    @Test
    public void cancel_discardsPageCountResult() throws InterruptedException {
        FakeCountableDocument slowDoc = new FakeCountableDocument(42, 3000L);
        AsyncDocLoader loader = new AsyncDocLoader(slowDoc, executor);
        AtomicBoolean callbackFired = new AtomicBoolean(false);

        loader.openDocument();
        loader.setOnPageCountListener((count) -> callbackFired.set(true));

        // Cancel immediately after starting
        loader.cancel();

        // Wait longer than the fake layout time
        Thread.sleep(4000);

        // Callback should NOT have fired because we cancelled
        assertFalse(callbackFired.get());
    }

    // --- Behavior 4: opening new document while counting cancels previous ---

    @Test
    public void openNewDocument_cancelsPreviousCount() throws InterruptedException {
        FakeCountableDocument doc1 = new FakeCountableDocument(42, 2000L);
        FakeCountableDocument doc2 = new FakeCountableDocument(99, 100L);
        AsyncDocLoader loader = new AsyncDocLoader(doc1, executor);

        AtomicInteger callbackCount = new AtomicInteger(0);
        AtomicInteger lastResultPages = new AtomicInteger(0);

        // Open first document (slow)
        loader.openDocument();
        loader.setOnPageCountListener((count) -> {
            callbackCount.incrementAndGet();
            lastResultPages.set(count);
        });

        // Let doc1 counting start
        Thread.sleep(100);

        // Open second document (fast) — cancels first
        loader.openDocumentWith(doc2);

        // Wait for doc2 to finish
        Thread.sleep(500);

        // Only one callback should have fired, with doc2's count
        assertEquals(1, callbackCount.get());
        assertEquals(99, lastResultPages.get());
    }

    // --- Fake CountableDocument for testing ---

    /**
     * A fake CountableDocument that simulates MuPDF page counting with a configurable delay.
     */
    static class FakeCountableDocument implements AsyncDocLoader.CountableDocument {
        private final int realPageCount;
        private final long layoutDelayMs;
        private volatile boolean counting = false;

        FakeCountableDocument(int realPageCount, long layoutDelayMs) {
            this.realPageCount = realPageCount;
            this.layoutDelayMs = layoutDelayMs;
        }

        @Override
        public int getPageCount(int w, int h, int fontSizeSp) {
            try {
                Thread.sleep(layoutDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0;
            }
            counting = false;
            return realPageCount;
        }

        @Override
        public boolean isCounting() {
            return counting;
        }

        @Override
        public void setIsCounting(boolean counting) {
            this.counting = counting;
        }
    }
}
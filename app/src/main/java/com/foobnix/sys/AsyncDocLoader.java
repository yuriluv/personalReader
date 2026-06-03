package com.foobnix.sys;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates asynchronous document opening and page counting.
 *
 * On {@link #openDocument()}, the document handle is returned immediately
 * with pageCount=0 and isCounting=true. The page count is computed on a
 * background thread, and the callback fires when it completes.
 *
 * This decouples the first-page render from the full page count, allowing
 * the UI to display the target chapter before all chapters are laid out.
 */
public class AsyncDocLoader {

    /**
     * Abstraction over CodecDocument's page counting, for testability.
     * Real implementation delegates to MuPdfDocument.
     */
    public interface CountableDocument {
        int getPageCount(int w, int h, int fontSizeSp);
        boolean isCounting();
        void setIsCounting(boolean counting);
    }

    /** Callback interface for when page count is available. */
    public interface OnPageCountListener {
        void onPageCountReady(int pageCount);
    }

    /** Result of opening a document. */
    public static class Result {
        public final CountableDocument document;
        public final int initialPageCount;

        Result(CountableDocument document, int initialPageCount) {
            this.document = document;
            this.initialPageCount = initialPageCount;
        }
    }

    private CountableDocument document;
    private final ExecutorService executor;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Future<?> countFuture;
    private OnPageCountListener listener;

    public AsyncDocLoader(CountableDocument document, ExecutorService executor) {
        this.document = document;
        this.executor = executor;
    }

    /**
     * Starts counting pages in the background.
     * Returns immediately — document.isCounting() will be true until counting completes.
     */
    public Result openDocument() {
        cancelled.set(false);
        document.setIsCounting(true);
        startCounting();
        return new Result(document, 0);
    }

    /**
     * Opens a new document, cancelling any previous count.
     */
    public Result openDocumentWith(CountableDocument newDocument) {
        cancel();
        this.document = newDocument;
        cancelled.set(false);
        document.setIsCounting(true);
        startCounting();
        return new Result(newDocument, 0);
    }

    private void startCounting() {
        final CountableDocument doc = this.document;
        countFuture = executor.submit(() -> {
            try {
                int count = doc.getPageCount(0, 0, 0);
                if (!cancelled.get()) {
                    doc.setIsCounting(false);
                    if (listener != null) {
                        listener.onPageCountReady(count);
                    }
                }
            } catch (Exception e) {
                doc.setIsCounting(false);
            }
        });
    }

    /**
     * Cancels the background page count. The listener will NOT be called.
     */
    public void cancel() {
        cancelled.set(true);
        if (countFuture != null) {
            countFuture.cancel(true);
        }
        if (document != null) {
            document.setIsCounting(false);
        }
    }

    public void setOnPageCountListener(OnPageCountListener listener) {
        this.listener = listener;
    }
}
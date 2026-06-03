package org.ebookdroid.droids;

import com.foobnix.android.utils.LOG;
import com.foobnix.ext.CacheZipUtils;
import com.foobnix.ext.EpubExtractor;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.JsonHelper;
import com.foobnix.pdf.info.model.BookCSS;

import org.ebookdroid.core.codec.CodecDocument;
import org.ebookdroid.droids.mupdf.codec.MuPdfDocument;
import org.ebookdroid.droids.mupdf.codec.PdfContext;

import java.io.File;
import java.util.Map;

public class
EpubContext extends PdfContext {

    private static final String TAG = "EpubContext";
    File cacheFile;

    @Override
    public File getCacheFileName(String fileNameOriginal) {
        LOG.d(TAG, "getCacheFileName", fileNameOriginal, AppSP.get().hypenLang);
        cacheFile = new File(CacheZipUtils.CACHE_BOOK_DIR, (fileNameOriginal +
                AppState.get().isReferenceMode +
                AppState.get().isShowPageNumbers +
                AppState.get().isShowFooterNotesInText +
                AppState.get().fullScreenMode +
                //AppState.get().isAccurateFontSize +
                BookCSS.get().documentStyle +
                BookCSS.get().isAutoHypens +
                AppState.get().isBionicMode +
                AppSP.get().hypenLang +
                AppState.get().enableImageScale +
                AppState.get().textReplacementHash +
                AppState.get().isExperimental)
                .hashCode() + ".epub");
        return cacheFile;
    }

    @Override
    public CodecDocument openDocumentInner(final String fileName, String password) {
        long t0 = System.currentTimeMillis();
        LOG.d(TAG, fileName);

        if (cacheFile == null) {
            cacheFile = getCacheFileName(fileName);
        }

        final boolean needsProcessing = AppState.get().isEnableTextReplacement || BookCSS.get().isAutoHypens || AppState.get().isReferenceMode || AppState.get().isShowFooterNotesInText;
        final boolean hasCache = cacheFile.isFile();

        // Determine bookPath: use cached file if available, otherwise original
        String bookPath;
        if (needsProcessing && hasCache) {
            // Cache hit — use processed EPUB (existing fast path)
            bookPath = cacheFile.getPath();
            android.util.Log.d("PERF-epub", "  EpubContext: cache HIT, using processed file");
        } else {
            // Cache miss or no processing needed — use original EPUB for immediate render
            bookPath = fileName;
            android.util.Log.d("PERF-epub", "  EpubContext: cache MISS, using original file");
        }

        final MuPdfDocument muPdfDocument = new MuPdfDocument(this, MuPdfDocument.FORMAT_PDF, bookPath, password);
        muPdfDocument.cacheFilename = bookPath;

        // Background thread: defer heavy processing
        Thread bgThread = new Thread("@T epubProcess") {
            @Override
            public void run() {
                try {
                    Map<String, String> notes = null;
                    if (AppState.get().isShowFooterNotesInText) {
                        notes = getNotes(fileName);
                        android.util.Log.d("PERF-epub", "  EpubContext:bg getNotes dt=" + (System.currentTimeMillis() - t0) + "ms");
                    }

                    if (needsProcessing && !hasCache) {
                        // Cache miss: process EPUB in background
                        long tph0 = System.currentTimeMillis();
                        EpubExtractor.proccessHypens(fileName, cacheFile.getPath(), notes);
                        android.util.Log.d("PERF-epub", "  EpubContext:bg proccessHypens dt=" + (System.currentTimeMillis() - tph0) + "ms");
                    }

                    // Set notes on document if available
                    if (notes != null && !muPdfDocument.isRecycled()) {
                        muPdfDocument.setFootNotes(notes);
                    }

                    if (muPdfDocument.getFootNotes() == null) {
                        muPdfDocument.setFootNotes(getNotes(fileName));
                    }
                    muPdfDocument.setMediaAttachment(EpubExtractor.getAttachments(fileName));

                    removeTempFilesIfCancel();
                } catch (Throwable e) {
                    LOG.e(e);
                    android.util.Log.d("PERF-epub", "  EpubContext:bg FAILED: " + e.getMessage());
                }
            }
        };
        bgThread.setPriority(Thread.MIN_PRIORITY);
        bgThread.start();

        android.util.Log.d("PERF-epub", "  EpubContext:openDocumentInner dt=" + (System.currentTimeMillis() - t0) + "ms");
        return muPdfDocument;
    }

    public Map<String, String> getNotes(String fileName) {
        Map<String, String> notes = null;
        final File jsonFile = new File(cacheFile + ".json");
        if (/** !LibreraBuildConfig.DEBUG && **/jsonFile.isFile()) {
            LOG.d("getNotes cache", fileName);
            notes = JsonHelper.fileToMap(jsonFile);
        } else {
            LOG.d("getNotes extract", fileName);
            notes = EpubExtractor.get().getFooterNotes(fileName);
            JsonHelper.mapToFile(jsonFile, notes);
            LOG.d("save notes to file", jsonFile);
        }
        return notes;
    }

}

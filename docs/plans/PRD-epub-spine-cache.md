# PRD: EPUB Spine-aware Split Cache

## Problem Statement

Opening an EPUB book in YuriReader is slow because every book open triggers the full Java preprocessing pipeline: ZIP extraction, HTML hyphenation, text replacement, footnote injection, and re-packaging into a new EPUB file. This happens even for books that were previously opened because the existing cache is a monolithic file — any rendering setting change (font size, margins, hyphenation language, document style, footer notes, bionic mode, etc.) invalidates the entire cache, forcing a full reprocess.

For first-time opens, the user sees a loading dialog while the entire EPUB is processed before any page is visible. For repeat opens with unchanged settings, the cache hit avoids reprocessing, but the cache key is fragile and any setting toggle forces a full redo.

This is especially painful for Korean web novel readers who frequently open new books (often serialized), where the first-open delay is the primary UX pain point.

## Solution

Introduce a **spine-aware split cache** that stores each processed spine HTML file individually under `CACHE_BOOK_DIR/{bookHash}_spine/`, alongside the original OPF, CSS, and static resources. When opening an EPUB:

1. If the split cache directory exists and is valid, `ImageExtractor.getNewCodecContext()` passes the **directory path** directly to MuPDF, bypassing the entire `EpubExtractor.proccessHypensApache()` pipeline.
2. If the cache does not exist, the book opens normally (existing monolithic cache path), and a background task creates the split cache for the next open.
3. MuPDF's `epub_open_document()` accepts both file paths and directory paths, so the split cache directory can serve as a self-contained EPUB archive.

This reduces the EPUB open path from:
```
open → extract ZIP → process all HTML → write new ZIP → MuPDF layout all chapters → display page 1
```
to:
```
open → check directory exists → MuPDF layout all chapters → display page 1
```

The preprocessing (hyphenation, text replacement) is moved to a background warming task that populates the cache for subsequent opens.

## User Stories

1. As a reader, I want previously opened EPUB books to open instantly, so that I don't have to wait for reprocessing every time.
2. As a reader, I want the first open of an EPUB to still show the book as fast as possible, even if the background cache isn't ready yet.
3. As a reader, I want changing font size or margins to invalidate only the affected spine cache, not force a full EPUB reprocess.
4. As a reader, I want the cache to survive across app restarts without reprocessing, so that my reading experience is consistently fast.
5. As a reader, I want cache storage to be bounded, so that reading many books doesn't fill up my device storage.
6. As a reader, I want EPUB books with embedded fonts, images, and CSS to render correctly when served from the split cache, so that the reading experience is identical to the original.
7. As a reader, I want footnote/endnote links to work correctly when served from the split cache, so that I can navigate references within the book.
8. As a reader, I want the app to handle corrupt or incomplete cache directories gracefully, falling back to the original EPUB file.
9. As a reader, I want the background cache-warming task to be cancellable, so that rapidly switching between books doesn't create zombie processing.
10. As a developer, I want the split cache structure to be a valid EPUB archive (with OPF pointing to renamed spine files), so that MuPDF can open it as a directory-based EPUB without format-specific hacks.
11. As a developer, I want the cache key to incorporate rendering settings that affect HTML content (hyphenation, text replacement, page numbers, footnotes, bionic mode), so that stale caches are never served.
12. As a developer, I want the OPF manifest to be rewritten to match the renamed `spine_NNNN_filename.html` files, so that MuPDF's spine traversal resolves correctly.
13. As a developer, I want existing monolithic cache behavior to be preserved as a fallback, so that the switch to split cache is incremental and reversible.
14. As a developer, I want to know whether MuPDF's `epub_open_document()` actually accepts directory paths as valid EPUB archives, so that I can confirm the core assumption before building the entire pipeline.
15. As a reader, I want the app to handle EPUBs with a single massive HTML file (e.g., 100+ MB) gracefully, even if full optimization is not possible for those cases.
16. As a reader, I want the cache to be cleared when I explicitly clear app data or cache, so that stale data doesn't accumulate.
17. As a developer, I want to measure the actual time saved by the split cache, so that I can validate the optimization is worth the complexity.
18. As a developer, I want the `META-INF/container.xml` file to be present in the split cache directory, so that MuPDF can locate the OPF correctly when opening a directory.

## Implementation Decisions

### ID-1: Cache directory structure
The split cache for each book is stored at `CACHE_BOOK_DIR/{hash}_spine/`, where `{hash}` is derived from the book file's identity (file path + last modified timestamp + rendering settings). The directory contains:
- `META-INF/container.xml` — points to the OPF
- `{OEBPS-path}/content.opf` — rewritten to reference `spine_NNNN_` filenames
- `spine_0001_OEBPS_chapter1.html`, `spine_0002_OEBPS_chapter2.html`, etc. — processed HTML files
- CSS, images, fonts, and other resources — copied as-is

### ID-2: OPF manifest rewriting
When `EpubExtractor.extractSpineToCache()` writes spine HTML files with `spine_NNNN_` prefixed names, the OPF manifest `<item>` elements must have their `href` attributes updated to match these new filenames. This is done with Jsoup after all files are written.

### ID-3: Cache validation key
The cache key must incorporate any rendering setting that affects HTML content. The existing `EpubContext.getCacheFileName()` already computes a hash of: `isReferenceMode`, `isShowPageNumbers`, `isShowFooterNotesInText`, `fullScreenMode`, `documentStyle`, `isAutoHypens`, `isBionicMode`, `hypenLang`, `enableImageScale`, `textReplacementHash`, `isExperimental`. The split cache key will use the same hash function, ensuring that any setting change invalidates the cache.

### ID-4: Background cache warming
When `HorizontalViewActivity` opens an EPUB and no split cache exists, it proceeds with the existing monolithic cache path. After the first page is visible, an `AsyncTask` fires `EpubExtractor.extractSpineToCache()` in the background to populate the split cache for subsequent opens.

### ID-5: MuPDF directory EPUB support
The core assumption is that MuPDF's `epub_open_document()` can open a directory as an EPUB archive, the same way it opens a `.epub` ZIP file. This must be validated by inspecting `Builder/jni/~mupdf-*/source/html/html-doc.c` or by testing with a directory containing `META-INF/container.xml`, an OPF, and spine HTML files. If this assumption fails, the fallback is to re-package the directory into a ZIP before passing to MuPDF.

### ID-6: Fallback on cache failure
If the split cache directory is corrupt, incomplete, or MuPDF rejects it, `ImageExtractor.getNewCodecContext()` falls back to the original EPUB file path and the existing monolithic cache path. This ensures the book always opens.

### ID-7: Modules modified
- **`CacheZipUtils`**: Add `getSpineCacheDir(File)` and `hasSpineCache(File)` helper methods.
- **`EpubExtractor`**: Add `extractSpineToCache(String inputPath, File cacheDir)` method that performs the two-pass extraction: OPF parsing + per-spine HTML processing.
- **`ImageExtractor`**: Add cache-directory check in `getNewCodecContext()` to prefer split cache when available.
- **`HorizontalViewActivity`**: Add background `AsyncTask` to warm the spine cache after book opens.

### ID-8: No C++ changes in this phase
MuPDF native code is not modified. The optimization targets the Java preprocessing layer only. MuPDF's full-document layout on `getPageCount()` remains a bottleneck, but is deferred to a future phase.

### ID-9: No persistent MuPDF accelerator
The MuPDF `epub_accelerator` mechanism (which stores `pages_in_chapter[]` to disk) is not implemented in this phase. It is deferred because its benefit is only for `getPageCount()` speed, and the split cache already removes the Java preprocessing cost.

## Testing Decisions

### What makes a good test
Tests should verify external behavior, not implementation details. For this feature, the key external behaviors are:
1. A cached EPUB opens faster than an uncached one.
2. The rendered content is identical whether served from cache or original.
3. Cache invalidation works correctly when settings change.
4. Corrupt/incomplete caches fall back gracefully.

### Test modules
- **`EpubExtractor.extractSpineToCache()`**: Unit test with a minimal EPUB ZIP fixture. Verify: (a) all spine files are written with `spine_NNNN_` prefix, (b) OPF manifest hrefs are rewritten, (c) CSS/images are copied as-is, (d) `META-INF/container.xml` is present.
- **`CacheZipUtils.getSpineCacheDir()` / `hasSpineCache()`**: Unit test verifying deterministic cache key generation and directory existence checks.
- **`ImageExtractor.getNewCodecContext()`**: Integration test verifying that when a spine cache exists, it is preferred over the original path. This test requires MuPDF to be available (instrumented test on device).
- **Manual integration test**: Open an EPUB for the first time, verify cache is created, re-open and verify speedup and logcat message.

### Prior art
The existing test structure in this project has no unit tests (`test/` and `androidTest/` directories don't exist yet). New tests should be placed in `app/src/test/java/com/foobnix/ext/` for unit tests and `app/src/androidTest/java/` for instrumented tests.

### Highest feasible test seam
The highest test seam is `EpubExtractor.extractSpineToCache()` — it takes an input path and output directory, and its output can be verified purely by inspecting files on disk. This avoids needing MuPDF or Android framework dependencies for the core logic test.

## Out of Scope

1. **TXT format optimization** — deferred to a future PRD.
2. **Persistent MuPDF accelerator** — the `epub_accelerator` disk serialization mechanism in MuPDF's C code is not implemented here.
3. **MuPDF C++ modifications** — no changes to MuPDF native code in this phase.
4. **Single-massive-HTML EPUB optimization** — EPUBs with one enormous HTML file are excluded from scope; TXT format is recommended for such content.
5. **WebView rendering** — explicitly excluded as an alternative renderer.
6. **Incremental page count** — `CodecDocument.getPageCount()` remains a synchronous call that requires MuPDF to layout all chapters; deferred.
7. **Progressive/background chapter layout** — deferred to a future phase that would modify MuPDF C++ to support partial layout.
8. **Cache eviction policy** — the existing `CacheZipUtils` eviction mechanism (remove oldest files when >50 entries) will be extended for spine cache directories, but sophisticated LRU or size-based eviction is out of scope.

## Further Notes

- The split cache approach works because MuPDF already treats each spine HTML as an independent chapter with its own page count. By serving pre-processed HTML files directly, we eliminate the Java preprocessing cost (hyphenation, text replacement, CSS filtering) that currently runs on every cache miss.
- Cache invalidation must mirror the existing `EpubContext.getCacheFileName()` hash computation to ensure settings changes correctly invalidate stale caches.
- The OPF rewriting step is critical: without it, MuPDF's spine traversal would fail because the referenced filenames in the manifest would not match the `spine_NNNN_` prefixed filenames on disk.
- This PRD assumes MuPDF can open a directory as an EPUB. If this assumption is invalid, the fallback is to zip the directory contents into a `.epub` file and pass that to MuPDF — adding a small cost but still avoiding the hyphenation/reprocessing step.
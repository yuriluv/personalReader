# YuriReader Domain Glossary

## Core Concepts

### Spine
The reading order of HTML files in an EPUB, defined by `<itemref>` elements in the OPF manifest. Each spine entry points to one HTML file that MuPDF treats as a separate "chapter."

### Accelerator
MuPDF's per-chapter page count cache stored as a binary file (`+accel` suffix). Contains `pages_in_chapter[]` array. Loaded on document open, saved after `getPageCount()`. Invalidated when font size, screen dimensions, or CSS change.

### Java Preprocessing
`EpubExtractor.proccessHypensApache()` — ZIP extraction, hyphenation, text replacement, footnote injection, CSS filtering. Runs only when no monolithic cache exists. Produces a processed `.epub` in `CACHE_BOOK_DIR`.

### Monolithic Cache
The existing single-file EPUB cache at `CACHE_BOOK_DIR/{hash}.epub`. Hash incorporates 12+ rendering settings. Any setting change invalidates the entire cache.

### Split Spine Cache (planned)
Per-spine-file cache directory at `CACHE_BOOK_DIR/{hash}_spine/`. Each processed HTML file stored individually. Allows skipping Java preprocessing on re-open.

### getPageCount() Bottleneck
MuPDF's `fz_count_pages()` requires laying out ALL chapters before returning. On first open (no accelerator), this is O(N × chapter_size). This is THE primary cause of first-open delay.

### Placeholder Page (current behavior)
When `pagesCount=1` (before `getPageCount()` completes), ViewPager shows a single empty page: solid background with "페이지 N" text. This is the transition/waiting screen, not actual book content. It persists until `onPageCountReady()` replaces it with the real book.

### Asynchronous Page Loading (implemented)
`openDocumentWithoutCount()` returns immediately with `pagesCount=1`. `resolvePageCount()` runs on background thread. `onPageCountReady()` updates ViewPager with real page count and restores saved position. This eliminates the 2-3.5s blocking delay on first open, but re-open still shows the placeholder page briefly before jumping to saved position (flash/flicker).

### Approach 1 — Non-Chapter Fast Load
Current `pagesCount=1` placeholder strategy, with re-open position restore: show placeholder page while `getPageCount()` runs in background, then jump to saved position on `onPageCountReady()`. No chapter-level rendering.

### Approach 2 — Chapter Fast Load (new)
Uses `epub_load_page(chapter, page_within_chapter)` via new JNI binding to render the target chapter immediately without waiting for `getPageCount()`. First open: chapter 0, page 0. Re-open: saved `chapterIdx` and `pageInChapter`. Full book overlays when background `getPageCount()` completes, with page-shift correction.

### Chapter Pre-Render
Approach 2's initial render of a single EPUB spine chapter via `epub_load_page()`. Bypasses `fz_count_pages()` entirely — only parses and layouts one HTML file (~10-50ms). User sees real book content, not a placeholder.

### Full-Book Overlay
When background `getPageCount()` completes, the pre-rendered chapter view is replaced by the full book ViewPager. Page position must be adjusted: `absolutePage = sum(pages_in_chapter[0..chapterIdx-1]) + pageInChapter`.

### PageShiftCalculator
Utility class for bidirectional conversion between absolute page numbers and (chapterIdx, pageInChapter) pairs. Uses `pagesInChapter[]` from `MuPdfDocument.getPagesInChapter()` JNI. Methods: `toAbsolutePage()` (chapter → absolute) and `toChapterPage()` (absolute → chapter).

### PageLoadStrategy
Enum controlling EPUB first-page rendering. Values: `CHAPTER_FAST` (0, default) — Approach 2 via `epub_load_page()`, `NON_CHAPTER_FAST` (1) — Approach 1 placeholder. Stored as `AppSP.pageLoadStrategy` int. User toggles via Settings > Advanced > "Chapter Fast Load".

### Accelerator Disable (Testing)
MuPDF's per-chapter page count cache. Temporarily disabled during testing via `AppSP.isDisableAccelerator` flag that nullifies the `accel` path in `openFile()`. Purpose: measure pure loading performance without caching. May be re-enabled or permanently removed based on test results.

### pagesInChapter Cache
`int[]` cached in `HorizontalModeController` after `onPageCountReady()`. Populated by `MuPdfDocument.getPagesInChapter()` JNI which calls `fz_count_chapters()` + `fz_count_pages_in_chapter()`. Used by `PageShiftCalculator` for page-shift correction on full-book overlay and for saving chapter-level positions on page change.

## Key Files

| File | Role |
|------|------|
| `EpubContext.java` | EPUB codec context — cache key, preprocessing decision |
| `AbstractCodecContext.java` | Base codec — cache check, extraction, dispatch |
| `ImageExtractor.java` | Bridge between UI and MuPDF — calls openDocument + getPageCount synchronously |
| `MuPdfDocument.java` | JNI wrapper — open(), getPageCount(), accelerator path, getPagesInChapter() |
| `MuPdfPage.java` | JNI page — open(), openChapterPage(), createChapterPage() |
| `EpubExtractor.java` | EPUB ZIP processing — hyphenation, text replacement |
| `HorizontalModeController.java` | UI controller — creates codec context, manages ViewPager, strategy branching, cachedPagesInChapter |
| `HorizontalViewActivity.java` | Activity hosting ViewPager |
| `PageShiftCalculator.java` | Bidirectional (chapter,page) ↔ absolute page conversion |
| `PageLoadStrategy.java` | Enum: CHAPTER_FAST vs NON_CHAPTER_FAST |
| `AppBook.java` | Book settings model — chapterIdx, pageInChapter, p, updateChapterPosition() |
| `AppSP.java` | App-level preferences — pageLoadStrategy, isDisableAccelerator |
| `libmupdf-librera.c` | JNI C — openChapterPage(), getPagesInChapter() |
| `epub-doc.c` | MuPDF EPUB — epub_load_page() (non-static), pages_in_chapter[] |

## Performance Phases

| Phase | Description | First Open | Re-open |
|-------|-------------|------------|---------|
| A | Java preprocessing (ZIP + hyphenation) | ~1-10s (book-dependent) | Skipped (monolithic cache) |
| B | MuPDF `getPageCount()` (layout all chapters) | ~0.8-1.3s (no accelerator) | ~few ms (accelerator hit) |
| C | First page render | ~100ms | ~100ms |
| D | Placeholder page → real content transition | visible flash | visible flash |
# PRD: EPUB First-Page Fast Rendering

## Problem Statement

When opening an EPUB book for the first time in YuriReader, the user must wait for the entire book to be processed before seeing any content. The delay ranges from 1 to 10+ seconds depending on book size, caused by two sequential blocking operations:

1. **Java preprocessing** (Phase A): `EpubExtractor.proccessHypensApache()` extracts the EPUB ZIP, applies hyphenation and text replacements, and writes a new processed EPUB. On re-open this is cached, but first-open always runs it.
2. **MuPDF full layout** (Phase B): `getPageCount()` calls `fz_layout_document()` + `fz_count_pages()`, which sequentially parses and lays out every chapter before returning. The UI cannot render any page until this returns.

The re-open path is already fast (~1s) thanks to the monolithic Java cache (Phase A skip) and MuPDF accelerator persistence (Phase B skip). But first-open remains unacceptably slow.

## Solution

Make the first visible page appear as fast as possible by **decoupling page rendering from the full page count**. Instead of blocking until `getPageCount()` returns, render the target chapter (first chapter or last-read position) immediately after its layout completes, while remaining chapters are laid out in the background.

Specifically:
1. **Async page count**: `getImageCount()` runs on a background thread. Upon completion, the total page count and scroll bar are updated.
2. **Immediate first-page render**: After `MuPdfDocument` opens (which only reads the spine, no layout), immediately request rendering of the target page. MuPDF will layout only the target chapter on demand, returning it before all chapters are processed.
3. **Progressive UI**: The ViewPager renders pages as their chapters become available. The progress bar and total page count update when `getPageCount()` completes.

## User Stories

1. As a reader, I want the first page of an EPUB to appear within 1 second of opening it, so that I can start reading immediately.
2. As a reader, I want the last page I was reading to appear quickly when re-opening a book, so that I can continue where I left off.
3. As a reader, I want the page count and scroll bar to update dynamically as chapters finish loading, so that I know the book is still being processed.
4. As a reader, I want to be able to scroll through already-loaded chapters while later chapters are still loading, so that I can start navigating before the full book is ready.
5. As a reader, I want the app to remain responsive (no ANR) during EPUB loading, so that I can cancel or navigate away if needed.
6. As a reader, I want this speed improvement to work for all EPUB books regardless of size, so that both small and large books benefit.
7. As a developer, I want the existing MuPDF accelerator mechanism preserved, so that re-open performance remains fast.
8. As a developer, I want the existing Java preprocessing cache preserved as a fallback, so that books with hyphenation/text replacement settings still work correctly.
9. As a developer, I want the async loading to be cancellable, so that rapidly switching between books doesn't create zombie layout threads.
10. As a developer, I want PageIndexMapper to be updated when the total page count changes, so that page references remain correct throughout the loading process.

## Implementation Decisions

### ID-1: Async getPageCount() — Thread Model
`ImageExtractor.getNewCodecContext()` currently runs on the UI thread (called from `HorizontalModeController` constructor). The `getPageCount()` call within it must be moved to a background thread.

**Decision**: Create a new `AsyncDocLoader` class that:
- Calls `openDocument()` synchronously on the calling thread (fast — only reads spine)
- Calls `getPageCount()` on a background `ExecutorService` thread
- Returns a `CodecDocument` immediately with `pageCount = 0` and a flag `isCounting = true`
- Notifies a callback when `pageCount` is available

### ID-2: First-Page Render Before Page Count
`MuPdfDocument.getPage(pageNumber)` already layouts individual chapters on demand via `fz_run_page_contents()`. If the caller knows which chapter contains the target page, it can render that page immediately.

**Decision**: After `openDocument()` returns, compute the target chapter index from `BookRecord.currentPageIndex` (or default to chapter 0 for first-open). Request a render of that page. MuPDF will layout only that chapter, which for a single chapter is fast (~100-500ms).

### ID-3: Page Count Update Mechanism
When `getPageCount()` completes on the background thread, the total page count must be propagated to:
- `HorizontalModeController.pagesCount`
- `PageIndicator` (page number display)
- `SeekBar` (scroll bar)

**Decision**: Use a callback interface `OnPageCountListener` on `AsyncDocLoader`. The callback fires on the UI thread and triggers `HorizontalModeController.onPageCountReady(int total)`, which updates the ViewPager adapter, progress bar, and page indicator.

### ID-4: Temporary Page Count of Zero
While `getPageCount()` is running, the ViewPager needs some way to display pages. 

**Decision**: Set `pagesCount = 0` initially. The ViewPager renders only the target page. When the background count completes, `pagesCount` is set to the real value, the adapter is refreshed (`notifyDataSetChanged()`), and the scroll bar range is updated. The user sees the target page immediately and can read it; navigation to other pages becomes available after the count completes.

### ID-5: BookRecord Chapter Index
Currently `BookRecord` stores `currentPageIndex` (absolute page number) but not which chapter that page belongs to. When `pagesCount` is unknown (still counting), we need to know which chapter to render.

**Decision**: For first-open (no `BookRecord`), render chapter 0 (first spine HTML). For re-open (with `BookRecord`), estimate the chapter from `currentPageIndex` using the accelerator's per-chapter page count range if available, or fall back to chapter 0. In a future phase, add `chapterIndex` to `BookRecord` for precise restoration.

### ID-6: Cancellation
When the user opens a different book while `getPageCount()` is still running for the previous book, the background task must be cancelled.

**Decision**: `AsyncDocLoader` holds a `Future<?>` for the background task. `HorizontalModeController.onCloseActivityFinal()` calls `AsyncDocLoader.cancel()`, which interrupts the background `getPageCount()` call. MuPDF's `fz_count_pages()` is not interruptible at the native level, but the Java thread will be interrupted and the result discarded when the next book opens.

### ID-7: No HTML Splitting in This Phase
HTML splitting (approach (b) from earlier discussions) is deferred to a future phase. In this phase, each spine HTML file remains one MuPDF chapter. The optimization comes entirely from rendering the target chapter before `getPageCount()` completes.

### ID-8: No Caching Changes in This Phase
The existing monolithic Java cache and MuPDF accelerator persistence are preserved unchanged. No split spine cache is introduced. This phase focuses entirely on the async rendering architecture.

### ID-9: Modules Modified

| Module | Change |
|--------|--------|
| `ImageExtractor` | Replace synchronous `getNewCodecContext()` with async version. Keep sync version for cover page extraction. |
| `HorizontalModeController` | Accept `CodecDocument` before page count is ready. Add `onPageCountReady(int)` callback handler. |
| `HorizontalViewActivity` | Handle lifecycle during async loading (rotation, back press). |
| `CodecDocument` / `MuPdfDocument` | Add `isCounting()` flag and `getPageCountAsync()` method. |
| `PageUrl` | Support rendering a single page when total count is unknown. |
| New: `AsyncDocLoader` | Orchestrates async document opening and page counting. |

### ID-10: Progressive Chapter Layout (Future Phase)
In a future phase, `getPageCount()` can be replaced with a progressive version that counts chapters one at a time, updating the UI incrementally. This requires MuPDF C++ changes to expose `count_chapter_pages()` individually and is out of scope for this PRD.

## Testing Decisions

### What makes a good test
Tests should verify that:
1. First page appears within 1 second regardless of book size
2. Total page count is eventually correct
3. Navigation works after page count is available
4. Rapid book switching doesn't cause crashes or ANR
5. Last-read position restoration works after page count completes

### Test modules
- **`AsyncDocLoader`**: Unit test — verify callback fires, cancellation works, thread safety
- **`HorizontalModeController`**: Instrumented test — verify page count update triggers adapter refresh
- **Manual integration test**: Open a large EPUB (>2MB, >100 chapters), verify first page appears within 1s, verify page count updates after ~2-5s, verify navigation works after update

### Prior art
No existing tests in this project. New test infrastructure needed.

### Highest feasible test seam
`AsyncDocLoader` — it orchestrates the async flow and can be tested with a mock `CodecDocument` that delays `getPageCount()`.

## Out of Scope

1. **HTML splitting (Approach A — per-paragraph)** — split each spine HTML at `<p>`/`<div>` boundaries into tiny MuPDF chapters. Makes individual chapter layout extremely fast (~10-50ms) but generates thousands of chapters. Deferred until async rendering (this PRD) is benchmarked. If async rendering alone doesn't achieve the &lt;1s first-page target for large books, Approach A will be the next optimization.
2. **Split spine cache** — no per-spine file caching. Existing monolithic cache preserved.
3. **Persistent MuPDF accelerator changes** — existing accelerator mechanism preserved as-is.
4. **TXT format optimization** — out of scope.
5. **WebView rendering** — explicitly excluded.
6. **MuPDF C++ modifications** — no native code changes in this phase.
7. **BookRecord chapter index** — adding `chapterIndex` to `BookRecord` is deferred. First-open defaults to chapter 0.
8. **Progressive page count updates** — updating page count chapter-by-chapter (instead of all at once) requires MuPDF C++ changes and is deferred.

## Further Notes

- The key insight enabling this PRD is that `MuPdfDocument.openFile()` (which calls `fz_open_accelerated_document`) only reads the EPUB spine and document metadata — it does NOT layout any pages. Page layout happens on demand via `getPage()` (single page render) or all at once via `getPageCount()`. This means we can open the document, render the target page, and defer `getPageCount()` to a background thread.
- The MuPDF accelerator is already functional for re-opens. This PRD only addresses the first-open path.
- `HorizontalModeController` currently holds a hard dependency on `pagesCount` being available at construction time. Making it tolerate `pagesCount = 0` temporarily is the most significant UI change in this PRD.
- Care must be taken with `PageIndexMapper` which translates between real page indices and display page indices. When `pagesCount` changes from 0 to the real value, the mapper must be rebuilt without losing the current visible page position.
# PRD: EPUB Dual-Mode Page Loading with Chapter-Level Fast Render

## Problem Statement

YuriReader currently uses a single page-loading strategy: open the document with `pagesCount=1` (a placeholder), then run `getPageCount()` on a background thread, and finally replace the placeholder with the full book. This has two problems:

1. **Placeholder page is useless**: While `pagesCount=1`, the user sees a solid background with "페이지 N" text — not actual book content. This persists for 0.8-1.3s on first open (no accelerator) until `getPageCount()` completes.
2. **Re-open flash/flicker**: On re-open, even though `getPageCount()` is fast (milliseconds with accelerator), the current flow still shows the placeholder first, then jumps to the saved position — a visible flash.

MuPDF can render individual chapters on demand via `epub_load_page(chapter, page_within_chapter)`, which only layouts that single chapter. This capability is not exposed through the current JNI interface, which only offers `fz_load_page(doc, absolute_pageno)` — requiring the full page count first.

## Solution

Implement two page-loading strategies, selectable via a toggle in Settings > Advanced:

1. **Approach 1 — Non-Chapter Fast Load** (current behavior, refined): Show the placeholder page while `getPageCount()` runs in the background. On first open, jump to page 1; on re-open, jump to the saved page. This is the existing behavior with the re-open position-restore fix added.
2. **Approach 2 — Chapter Fast Load** (new): Use a new JNI function to call `epub_load_page(chapter, page_within_chapter)` directly, rendering the target chapter immediately without waiting for the full page count. On first open, render chapter 0 page 0; on re-open, render the saved chapter and page. After `getPageCount()` completes in the background, overlay the full book, adjusting the current position if the pre-rendered chapter's page offset has shifted.

Both approaches share the same `openDocument()` path — only the rendering strategy differs.

## User Stories

1. As a reader, I want to choose between two page-loading strategies, so that I can pick the one that works best for my reading habits.
2. As a reader using Approach 2, I want the first chapter to appear within 100ms of opening a new book, so that I can start reading immediately without seeing a placeholder.
3. As a reader using Approach 2 on re-open, I want to see my last-read chapter immediately, so that I can continue reading without flash or flicker.
4. As a reader using Approach 1 on re-open, I want the app to jump to my saved page once the page count finishes, so that I resume where I left off (even though there is a brief placeholder).
5. As a reader, I want the page count, scroll bar, and navigation to update once `getPageCount()` completes, regardless of which approach I use.
6. As a reader using Approach 2, I want my position within the pre-rendered chapter to be preserved when the full book overlays, so that I don't lose my reading progress.
7. As a developer, I want MuPDF accelerator disabled during testing, so that I can measure pure loading performance without caching effects.
8. As a developer, I want debug logging in the app to trace page-loading timing via ADB, so that I can validate both approaches on device.
9. As a reader, I want the toggle setting to persist across app restarts, so that I don't have to re-select my preferred strategy.
10. As a reader using Approach 2, I want the app to handle the case where I scroll beyond the pre-rendered chapter while `getPageCount()` is still running, so that I see content rather than blank pages or crashes.
11. As a developer, I want the Approach 2 JNI binding to be minimal and safe, so that it doesn't introduce native crashes or memory leaks.
12. As a developer, I want both approaches to use the same `onPageCountReady()` handler for the full-book overlay, so that code duplication is minimized.
13. As a reader, I want the default strategy to be Approach 2 (Chapter Fast Load), so that new users get the best experience out of the box.

## Implementation Decisions

### ID-1: Settings Toggle — Page Load Strategy

Add a toggle in Settings > Advanced (topmost position) with two options:
- "Chapter Fast Load" (Approach 2, default)
- "Non-Chapter Fast Load" (Approach 1)

Stored in `AppSP` or `AppState` as an enum/integer preference. The toggle controls which `HorizontalModeController` code path runs after `openDocument()`.

### ID-2: Approach 1 — Non-Chapter Fast Load (Re-open Fix)

This is the current `pagesCount=1` placeholder strategy with one addition: on re-open, save and restore the last-read page number so `onPageCountReady()` can jump directly to it.

Changes:
- `AppBook` currently stores only `p` (float, 0.0-1.0 percent). No new fields needed for Approach 1 — `p * totalPages` after `getPageCount()` completes gives the absolute page.
- `onPageCountReady()` reads `p`, computes `currentPage = Math.round(p * totalPages) - 1`, and calls `setCurrentItem(currentPage, false)` without animation.
- First-open: `currentPage = 0` (same as current behavior).

### ID-3: Approach 2 — Chapter Fast Load (New JNI Function)

Add a new JNI function that exposes `epub_load_page(chapter, page_within_chapter)`:

```c
// New JNI function in libmupdf-librera.c
JNIEXPORT jlong JNICALL
Java_org_ebookdroid_droids_mupdf_codec_MuPdfDocument_openChapterPage(
    JNIEnv *env, jclass cls, jlong handle, jint chapter, jint page);
```

This function:
1. Validates that `handle` is a valid MuPDF document.
2. Looks up the spine to find `chapter` index (0-based).
3. Calls `epub_get_laid_out_html()` for that chapter only.
4. Returns a page handle that can be rendered via the existing `MuPdfPage` rendering pipeline.

The chapter renders in ~10-50ms (single HTML parse + layout), vs ~800-1300ms for full `getPageCount()`.

### ID-4: AppBook Schema Change — Chapter Fields for Approach 2

Add two new fields to `AppBook`:

```java
public int chapterIdx;   // chapter index within EPUB spine (0-based), -1 if unknown
public int pageInChapter; // page offset within the chapter (0-based), -1 if unknown
```

- On `currentPageChanged()`: both fields are updated from the current page position.
- On re-open with Approach 2: `epub_load_page(chapterIdx, pageInChapter)` is called.
- Default values: `chapterIdx = 0`, `pageInChapter = 0` (first-open).
- Gson serializes these as `chapterIdx` / `pageInChapter` (explicit naming, no `@SerializedName` needed).
- These fields are only used by Approach 2. Approach 1 continues using `p` (percent).

### ID-5: Chapter Pre-Render Flow (Approach 2)

After `openDocument()` returns, if Approach 2 is selected:

1. Set `pagesCount` to the pre-rendered chapter's page count (1 chapter = N pages).
2. Call `openChapterPage(chapterIdx, pageInChapter)` via the new JNI function.
3. Display the rendered chapter in the ViewPager.
4. Start `getPageCount()` on background thread (same as current flow).
5. When `onPageCountReady()` fires:
   a. Compute the absolute page offset: `sum(pages_in_chapter[0..chapterIdx-1]) + pageInChapter`.
   b. Update `pagesCount` to the real total.
   c. Call `notifyDataSetChanged()` on the adapter.
   d. Set `currentPage` to the computed absolute offset (no animation).
   e. If the user scrolled beyond the pre-rendered chapter's pages, clamp to the last known position.

### ID-6: MuPDF Accelerator Disable for Testing

Add a temporary boolean flag (not exposed in UI) that, when set:
- Passes `null` or empty string as the `accel` parameter to `MuPdfDocument.open()`, forcing MuPDF to skip accelerator load/save.
- This forces `getPageCount()` to always do a full layout (0.8-1.3s).
- The flag is controlled via `BuildConfig.DEBUG` or a debug settings entry, not the user-facing toggle.
- If testing shows acceptable performance without the accelerator, it will be permanently removed. Otherwise, it will be re-enabled.

### ID-7: Debug Timing via ADB

Add `PERF-epub` tagged log statements at key points to measure timing via `adb logcat`:

- `PERF-epub: openDocument start/end` — document open duration
- `PERF-epub: chapterPreRender start/end` — Approach 2 chapter render duration
- `PERF-epub: getPageCount start/end` — full page count duration
- `PERF-epub: onPageCountReady` — time from open to full book available
- `PERF-epub: approach` — which strategy is active (1 or 2)

All log statements gated by `BuildConfig.DEBUG`.

### ID-8: Page Shift Handling on Full-Book Overlay

When `getPageCount()` completes and the ViewPager transitions from pre-rendered chapter to full book:

- The pre-rendered chapter's page N becomes absolute page `sum(pages_in_chapter[0..chapterIdx-1]) + N` in the full book.
- Compute this offset and set it as the current page.
- If the user has scrolled within the pre-rendered chapter, track `pageInChapter` from the ViewPager's current position relative to the chapter start.
- On overlay, update `chapterIdx` and `pageInChapter` fields in `AppBook`.

### ID-9: Modules Modified

| Module | Change |
|--------|--------|
| `AppBook.java` | Add `chapterIdx` and `pageInChapter` fields. Update `currentPageChanged()` to record them. |
| `AppState.java` / `AppSP.java` | Add page load strategy preference (enum: CHAPTER_FAST, NON_CHAPTER_FAST). |
| `SettingsFragment` / preferences XML | Add toggle in Advanced settings (topmost position). |
| `HorizontalModeController.java` | Branch on strategy after `openDocument()`. Approach 1: current flow. Approach 2: call chapter pre-render, then `getPageCount()` in background. |
| `HorizontalViewActivity.java` | Handle `onPageCountReady()` page shift for Approach 2. |
| `MuPdfDocument.java` | Add `openChapterPage(int chapter, int page)` JNI method. Add accelerator-disable flag handling. |
| `libmupdf-librera.c` | Add `Java_..._openChapterPage` JNI function wrapping `epub_load_page`. |
| `ImageExtractor.java` | Pass strategy selection through to `HorizontalModeController`. |

## Testing Decisions

### What makes a good test

Tests should verify:
1. Approach 1 (Non-Chapter): placeholder shows, then jumps to saved position on re-open.
2. Approach 2 (Chapter): chapter 0 renders within 100ms on first open; saved chapter renders within 100ms on re-open.
3. Page shift calculation: when full book overlays, current reading position does not jump.
4. Toggle persistence: setting survives app restart.
5. Accelerator disable: `getPageCount()` always takes 0.8-1.3s when accelerator is off.
6. No crashes when scrolling beyond pre-rendered chapter while `getPageCount()` is running.

### Test modules

- **`HorizontalModeController`**: Instrumented test — verify both strategy code paths produce correct ViewPager state.
- **`AppBook`**: Unit test — verify `chapterIdx`/`pageInChapter` serialization and deserialization.
- **Manual integration test**: 
  - Open a large EPUB (>2MB, >100 chapters) with Approach 2, verify first chapter renders within 100ms.
  - Re-open same EPUB, verify saved chapter renders within 100ms.
  - Toggle to Approach 1, verify placeholder → jump behavior.
  - Enable accelerator disable, verify `getPageCount()` takes >0.5s.
  - Check `adb logcat -s PERF-epub` for timing measurements.

### Prior art

No existing tests in this project. New test infrastructure needed.

### Highest feasible test seam

`HorizontalModeController.onPageCountReady()` — this is where both strategies converge. A mock `CodecDocument` that delays `getPageCount()` can test the overlay behavior without MuPDF.

## Out of Scope

1. **Accelerator permanent removal** — decided after testing. Currently disabled for testing only.
2. **Per-paragraph HTML splitting (Approach A from prior PRD)** — deferred indefinitely.
3. **Progressive page count updates** — updating page count chapter-by-chapter instead of all at once. Would require additional MuPDF C changes and is deferred.
4. **Monolithic cache changes** — existing Java preprocessing cache preserved.
5. **TXT, PDF, or other format optimization** — EPUB only.
6. **WebView rendering** — explicitly excluded.

## Further Notes

- The key insight for Approach 2 is that `epub_load_page(chapter, page_within_chapter)` is an internal MuPDF function that layouts only a single chapter. It does not require `fz_count_pages()` to have run. This is already used within MuPDF when `fz_load_page()` resolves a chapter internally, but it is not exposed through the current JNI interface.
- `epub_get_laid_out_html()` also writes back to the accelerator via `accelerate_chapter()` — so individual chapter renders contribute to the accelerator. Even with accelerator disabled, this side effect exists. The test flag should disable both reading and writing of the accelerator file.
- The `openDocument()` call (which reads the EPUB spine) is already fast at 5-11ms. Both approaches share this step.
- Approach 2's `chapterIdx` and `pageInChapter` fields default to -1. When -1, the app falls back to Approach 1 behavior (first chapter, first page). This handles books opened before the schema update where these fields don't exist in the serialized `AppBook`.

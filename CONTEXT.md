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

### Asynchronous Page Loading (planned)
Decoupling page rendering from `getPageCount()`. Render the first visible chapter immediately, update total page count asynchronously as remaining chapters are laid out.

### Approach A — Per-paragraph HTML Splitting (deferred)
Split each spine HTML file at `<p>` or `<div>` boundaries into smaller MuPDF chapters. Each chunk becomes its own chapter, making individual chapter layout extremely fast (~10-50ms per chunk). Combined with async getPageCount(), the target chunk would render almost instantly. Downside: generates thousands of chapters per book, increasing MuPDF overhead. Viable as a future optimization if async rendering alone is insufficient for large books.

## Key Files

| File | Role |
|------|------|
| `EpubContext.java` | EPUB codec context — cache key, preprocessing decision |
| `AbstractCodecContext.java` | Base codec — cache check, extraction, dispatch |
| `ImageExtractor.java` | Bridge between UI and MuPDF — calls openDocument + getPageCount synchronously |
| `MuPdfDocument.java` | JNI wrapper — open(), getPageCount(), accelerator path |
| `EpubExtractor.java` | EPUB ZIP processing — hyphenation, text replacement |
| `HorizontalModeController.java` | UI controller — creates codec context, manages ViewPager |
| `HorizontalViewActivity.java` | Activity hosting ViewPager |

## Performance Phases

| Phase | Description | First Open | Re-open |
|-------|-------------|------------|---------|
| A | Java preprocessing (ZIP + hyphenation) | ~1-10s (book-dependent) | Skipped (monolithic cache) |
| B | MuPDF `getPageCount()` (layout all chapters) | ~1-10s (book-dependent) | Skipped (accelerator) |
| C | First page render | ~100ms | ~100ms |
package com.foobnix.model;

/**
 * Page loading strategy for EPUB books.
 *
 * Controls how YuriReader renders the first visible page when opening a book.
 * Selectable via Settings > Advanced toggle.
 */
public enum PageLoadStrategy {

    /** Chapter Fast Load (Approach 2): pre-render target chapter via epub_load_page(). */
    CHAPTER_FAST(0),

    /** Non-Chapter Fast Load (Approach 1): show placeholder page, then jump to saved position. */
    NON_CHAPTER_FAST(1);

    /** Default strategy for new installs. */
    public static final PageLoadStrategy DEFAULT = CHAPTER_FAST;

    private final int value;

    PageLoadStrategy(int value) {
        this.value = value;
    }

    /** Convert to int for SharedPreferences persistence. */
    public int toInt() {
        return value;
    }

    /** Convert from int (SharedPreferences) back to enum. Returns DEFAULT for unknown values. */
    public static PageLoadStrategy fromInt(int value) {
        for (PageLoadStrategy s : values()) {
            if (s.value == value) return s;
        }
        return DEFAULT;
    }
}
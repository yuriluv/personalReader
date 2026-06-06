package com.foobnix.pdf.search.activity;

/**
 * Calculates the absolute page number in a full book
 * from a (chapterIdx, pageInChapter) pair and the pages_in_chapter array.
 *
 * Used when the full-book overlay replaces a chapter pre-render (Approach 2):
 * the user was reading chapter N at page M, and we need to compute
 * where they are in the fully-laid-out book.
 */
public class PageShiftCalculator {

    /**
     * Convert (chapterIdx, pageInChapter) to absolute page number.
     *
     * @param chapterIdx     0-based chapter index, or -1 if unknown
     * @param pageInChapter  0-based page offset within the chapter, or -1 if unknown
     * @param pagesInChapter per-chapter page counts from getPageCount(), or null
     * @return absolute 0-based page number, or -1 if inputs are invalid
     */
    public static int toAbsolutePage(int chapterIdx, int pageInChapter, int[] pagesInChapter) {
        if (chapterIdx < 0 || pageInChapter < 0 || pagesInChapter == null || pagesInChapter.length == 0) {
            return -1;
        }
        if (chapterIdx >= pagesInChapter.length) {
            return -1;
        }
        if (pageInChapter >= pagesInChapter[chapterIdx]) {
            return -1;
        }

        int offset = 0;
        for (int i = 0; i < chapterIdx; i++) {
            offset += pagesInChapter[i];
        }
        return offset + pageInChapter;
    }

    /**
     * Convert an absolute 0-based page number to (chapterIdx, pageInChapter).
     *
     * @param absolutePage  0-based absolute page number
     * @param pagesInChapter per-chapter page counts
     * @return int[2] = {chapterIdx, pageInChapter}, or null if inputs are invalid
     */
    public static int[] toChapterPage(int absolutePage, int[] pagesInChapter) {
        if (absolutePage < 0 || pagesInChapter == null || pagesInChapter.length == 0) {
            return null;
        }
        int remaining = absolutePage;
        for (int ch = 0; ch < pagesInChapter.length; ch++) {
            if (remaining < pagesInChapter[ch]) {
                return new int[]{ch, remaining};
            }
            remaining -= pagesInChapter[ch];
        }
        // absolutePage exceeds total pages
        return null;
    }
}
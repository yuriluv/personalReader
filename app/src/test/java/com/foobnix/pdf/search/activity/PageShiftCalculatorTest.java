package com.foobnix.pdf.search.activity;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for page shift calculation when full-book overlay replaces chapter pre-render.
 *
 * Behavior under test:
 * Given a chapter index and page offset within that chapter,
 * and the pages_in_chapter array from getPageCount(),
 * compute the absolute page number in the full book.
 */
public class PageShiftCalculatorTest {

    // --- Behavior 1: first chapter ---

    @Test
    public void firstChapter_pageZero_mapsToAbsoluteZero() {
        int[] pagesInChapter = {5, 10, 8, 12};
        int absolutePage = PageShiftCalculator.toAbsolutePage(0, 0, pagesInChapter);
        assertEquals(0, absolutePage);
    }

    @Test
    public void firstChapter_pageTwo_mapsToAbsoluteTwo() {
        int[] pagesInChapter = {5, 10, 8, 12};
        int absolutePage = PageShiftCalculator.toAbsolutePage(0, 2, pagesInChapter);
        assertEquals(2, absolutePage);
    }

    // --- Behavior 2: middle chapter ---

    @Test
    public void secondChapter_pageZero_mapsToSumOfPreviousChapters() {
        int[] pagesInChapter = {5, 10, 8, 12};
        // chapter 0 has 5 pages, so chapter 1 page 0 = absolute page 5
        int absolutePage = PageShiftCalculator.toAbsolutePage(1, 0, pagesInChapter);
        assertEquals(5, absolutePage);
    }

    @Test
    public void thirdChapter_pageThree_mapsCorrectly() {
        int[] pagesInChapter = {5, 10, 8, 12};
        // chapter 0: 5, chapter 1: 10 → offset 15, chapter 2 page 3 = absolute 18
        int absolutePage = PageShiftCalculator.toAbsolutePage(2, 3, pagesInChapter);
        assertEquals(18, absolutePage);
    }

    // --- Behavior 3: last chapter ---

    @Test
    public void lastChapter_lastPage_mapsToLastAbsolutePage() {
        int[] pagesInChapter = {5, 10, 8, 12};
        // total = 35 pages, last page index = 34
        int absolutePage = PageShiftCalculator.toAbsolutePage(3, 11, pagesInChapter);
        assertEquals(34, absolutePage);
    }

    // --- Behavior 4: edge cases ---

    @Test
    public void unknownChapter_minusOne_returnsMinusOne() {
        int[] pagesInChapter = {5, 10, 8, 12};
        int absolutePage = PageShiftCalculator.toAbsolutePage(-1, -1, pagesInChapter);
        assertEquals(-1, absolutePage);
    }

    @Test
    public void emptyPagesInChapter_returnsMinusOne() {
        int[] pagesInChapter = {};
        int absolutePage = PageShiftCalculator.toAbsolutePage(0, 0, pagesInChapter);
        assertEquals(-1, absolutePage);
    }

    @Test
    public void chapterOutOfBounds_returnsMinusOne() {
        int[] pagesInChapter = {5, 10};
        int absolutePage = PageShiftCalculator.toAbsolutePage(5, 0, pagesInChapter);
        assertEquals(-1, absolutePage);
    }

    @Test
    public void pageOutOfBounds_inChapter_returnsMinusOne() {
        int[] pagesInChapter = {5, 10};
        // chapter 0 has 5 pages (0..4), page 5 is out of bounds
        int absolutePage = PageShiftCalculator.toAbsolutePage(0, 5, pagesInChapter);
        assertEquals(-1, absolutePage);
    }

    @Test
    public void nullPagesInChapter_returnsMinusOne() {
        int absolutePage = PageShiftCalculator.toAbsolutePage(0, 0, null);
        assertEquals(-1, absolutePage);
    }
}
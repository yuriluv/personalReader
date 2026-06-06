package com.foobnix.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for PageLoadStrategy enum and its integration with AppSP.
 *
 * Behavior under test:
 * 1. PageLoadStrategy has CHAPTER_FAST and NON_CHAPTER_FAST values
 * 2. Default strategy is CHAPTER_FAST
 * 3. Strategy can be converted to/from int for SharedPreferences persistence
 */
public class PageLoadStrategyTest {

    // --- Behavior 1: enum values exist ---

    @Test
    public void chapterFastEnumExists() {
        assertNotNull(PageLoadStrategy.valueOf("CHAPTER_FAST"));
    }

    @Test
    public void nonChapterFastEnumExists() {
        assertNotNull(PageLoadStrategy.valueOf("NON_CHAPTER_FAST"));
    }

    // --- Behavior 2: default is CHAPTER_FAST ---

    @Test
    public void defaultStrategy_isChapterFast() {
        assertEquals(PageLoadStrategy.CHAPTER_FAST, PageLoadStrategy.DEFAULT);
    }

    // --- Behavior 3: int round-trip for SharedPreferences ---

    @Test
    public void chapterFast_toInt_andBack() {
        int value = PageLoadStrategy.CHAPTER_FAST.toInt();
        assertEquals(PageLoadStrategy.CHAPTER_FAST, PageLoadStrategy.fromInt(value));
    }

    @Test
    public void nonChapterFast_toInt_andBack() {
        int value = PageLoadStrategy.NON_CHAPTER_FAST.toInt();
        assertEquals(PageLoadStrategy.NON_CHAPTER_FAST, PageLoadStrategy.fromInt(value));
    }

    @Test
    public void fromInt_unknownValue_returnsDefault() {
        assertEquals(PageLoadStrategy.DEFAULT, PageLoadStrategy.fromInt(99));
    }

    @Test
    public void fromInt_negativeValue_returnsDefault() {
        assertEquals(PageLoadStrategy.DEFAULT, PageLoadStrategy.fromInt(-1));
    }
}
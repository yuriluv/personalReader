package com.foobnix.model;

import org.junit.Test;
import org.librera.LinkedJSONObject;

import static org.junit.Assert.*;

/**
 * Tests for AppBook chapter-level position fields (chapterIdx, pageInChapter).
 *
 * Behavior under test:
 * 1. New AppBook defaults chapterIdx=-1, pageInChapter=-1 (unknown)
 * 2. Fields survive JSON round-trip via Objects.toJSONObject/loadFromJson
 * 3. Old JSON without these fields loads with default values (backward compat)
 * 4. hashCode includes both fields (changes trigger save)
 */
public class AppBookChapterFieldsTest {

    // --- Behavior 1: defaults ---

    @Test
    public void newAppBook_defaultsChapterIdxToMinusOne() {
        AppBook book = new AppBook("/test/book.epub");
        assertEquals(-1, book.chapterIdx);
    }

    @Test
    public void newAppBook_defaultsPageInChapterToMinusOne() {
        AppBook book = new AppBook("/test/book.epub");
        assertEquals(-1, book.pageInChapter);
    }

    // --- Behavior 2: JSON round-trip ---

    @Test
    public void chapterFields_surviveJsonRoundTrip() {
        AppBook original = new AppBook("/test/book.epub");
        original.chapterIdx = 5;
        original.pageInChapter = 3;

        LinkedJSONObject json = com.foobnix.android.utils.Objects.toJSONObject(original);

        AppBook restored = new AppBook("/test/book.epub");
        com.foobnix.android.utils.Objects.loadFromJson(restored, json);

        assertEquals(5, restored.chapterIdx);
        assertEquals(3, restored.pageInChapter);
    }

    // --- Behavior 3: backward compatibility ---

    @Test
    public void oldJsonWithoutChapterFields_loadsWithDefaults() {
        // Simulate old JSON that only has 'p' and other legacy fields
        LinkedJSONObject oldJson = new LinkedJSONObject();
        oldJson.put("p", 0.5);
        oldJson.put("z", 100);
        // no chapterIdx, no pageInChapter

        AppBook restored = new AppBook("/test/book.epub");
        com.foobnix.android.utils.Objects.loadFromJson(restored, oldJson);

        assertEquals(-1, restored.chapterIdx);
        assertEquals(-1, restored.pageInChapter);
        assertEquals(0.5f, restored.p, 0.001f);
    }

    // --- Behavior 4: hashCode includes new fields ---

    @Test
    public void hashCode_changesWhenChapterIdxChanges() {
        AppBook book1 = new AppBook("/test/book.epub");
        book1.chapterIdx = 0;
        book1.pageInChapter = 0;

        AppBook book2 = new AppBook("/test/book.epub");
        book2.chapterIdx = 1;
        book2.pageInChapter = 0;

        assertNotEquals(book1.hashCode(), book2.hashCode());
    }

    @Test
    public void hashCode_changesWhenPageInChapterChanges() {
        AppBook book1 = new AppBook("/test/book.epub");
        book1.chapterIdx = 2;
        book1.pageInChapter = 0;

        AppBook book2 = new AppBook("/test/book.epub");
        book2.chapterIdx = 2;
        book2.pageInChapter = 5;

        assertNotEquals(book1.hashCode(), book2.hashCode());
    }
}
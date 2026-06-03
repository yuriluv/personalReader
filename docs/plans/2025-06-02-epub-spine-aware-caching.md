# EPUB Spine-aware Split Caching Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Eliminate the EPUB preprocessing bottleneck by caching each spine HTML file individually so MuPDF receives an already-extracted, already-processed directory instead of a raw ZIP. This removes ZIP extraction and HTML reprocessing (hyphenation, text replacement) from the critical path on every book open.

**Architecture:**
- `EpubExtractor` writes each processed spine HTML to its own file under `CACHE_BOOK_DIR/{bookHash}/spine_{index}.html`.
- OPF, CSS, and static resources are also copied into that directory tree.
- `ImageExtractor.getNewCodecContext()` checks for this cache directory first. If present, it passes the **directory path** to MuPDF instead of the original EPUB file path. MuPDF's `epub_open_document` accepts both file and directory archives.
- `HorizontalViewActivity` triggers a background `CopyAsyncTask` to pre-process and cache any remaining uncached spine files after the first page is already visible.

**Tech Stack:** Java 8, Android Gradle Plugin, MuPDF JNI (no C++ changes in this plan).

**Scope:** EPUB only. TXT deferred. Persistent accelerator cache deferred. Native renderer replacement deferred.

---

## Prerequisites

Confirm these paths exist in the working copy before starting:

```bash
ls ~/workspace/repos/personalReader/app/src/main/java/com/foobnix/ext/EpubExtractor.java
ls ~/workspace/repos/personalReader/app/src/main/java/com/foobnix/sys/ImageExtractor.java
ls ~/workspace/repos/personalReader/app/src/main/java/com/foobnix/pdf/search/activity/HorizontalViewActivity.java
ls ~/workspace/repos/personalReader/app/src/main/java/com/foobnix/ext/CacheZipUtils.java
ls ~/workspace/repos/personalReader/app/src/main/java/com/foobnix/ext/Fb2Extractor.java
```

Expected: all five files exist.

---

## Task 1: Add spine cache key helper to CacheZipUtils

**Objective:** Provide a deterministic directory path for a book's split spine cache.

**Files:**
- Modify: `app/src/main/java/com/foobnix/ext/CacheZipUtils.java:90`

**Step 1: Add helper methods**

```java
public static File getSpineCacheDir(File bookFile) {
    String hash = makeKey(bookFile);
    File dir = new File(CACHE_BOOK_DIR, hash + "_spine");
    if (!dir.exists()) {
        dir.mkdirs();
    }
    return dir;
}

public static boolean hasSpineCache(File bookFile) {
    File dir = getSpineCacheDir(bookFile);
    return dir.exists() && dir.listFiles() != null && dir.listFiles().length > 0;
}
```

**Step 2: Verify compilation**

```bash
cd ~/workspace/repos/personalReader
./gradlew :app:compileFdroidDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL (or pre-existing errors only, no new ones in CacheZipUtils).

**Step 3: Commit**

```bash
git add app/src/main/java/com/foobnix/ext/CacheZipUtils.java
git commit -m "feat: add spine cache directory helpers"
```

---

## Task 2: Add split-extraction method to EpubExtractor

**Objective:** Extract and process each spine HTML file individually into the cache directory, preserving OPF/CSS/resources.

**Files:**
- Modify: `app/src/main/java/com/foobnix/ext/EpubExtractor.java:80`
- Create: test helper (optional, see Task 6)

**Step 1: Add `extractSpineToCache()`**

Insert the following method inside `EpubExtractor` (after `proccessHypensApache`):

```java
public static void extractSpineToCache(String inputPath, File cacheDir) throws Exception {
    LOG.d("extractSpineToCache", inputPath, cacheDir);

    ZipArchiveInputStream zipInputStream = Zips.buildZipArchiveInputStream(inputPath);
    ArchiveEntry nextEntry = null;

    List<String> spine = new ArrayList<>();
    Map<String, String> manifest = new HashMap<>();

    // First pass: read OPF to build spine/manifest map
    while ((nextEntry = zipInputStream.getNextEntry()) != null) {
        String nameLow = nextEntry.getName().toLowerCase(Locale.US);
        if (nameLow.endsWith(".opf")) {
            XmlPullParser xpp = XmlParser.buildPullParser();
            xpp.setInput(zipInputStream, "utf-8");
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if ("item".equals(xpp.getName())) {
                        String id = xpp.getAttributeValue(null, "id");
                        String href = xpp.getAttributeValue(null, "href");
                        manifest.put(href, id);
                    } else if ("itemref".equals(xpp.getName())) {
                        String idref = xpp.getAttributeValue(null, "idref");
                        String linear = xpp.getAttributeValue(null, "linear");
                        if (!"no".equals(linear)) {
                            spine.add(idref);
                        }
                    }
                }
                eventType = xpp.next();
            }
        }
    }
    zipInputStream.close();

    List<SimpleMeta> replacements = AppData.get().getAllTextReplaces();
    Map<String, String> notes = new HashMap<>(); // TODO: load footnotes if needed
    Map<String, String> svgs = new HashMap<>();

    // Second pass: write each spine HTML as a separate file, plus resources
    zipInputStream = Zips.buildZipArchiveInputStream(inputPath);
    while ((nextEntry = zipInputStream.getNextEntry()) != null) {
        if (TempHolder.get().loadingCancelled.get()) {
            break;
        }
        String name = nextEntry.getName();
        String nameLow = name.toLowerCase(Locale.US);

        // Resources: copy as-is
        if (!nameLow.endsWith("html") && !nameLow.endsWith("htm") && !nameLow.endsWith("xml") && !nameLow.endsWith(".opf")) {
            File outFile = new File(cacheDir, name);
            outFile.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(outFile);
            StreamUtils.copy(zipInputStream, fos);
            fos.close();
            continue;
        }

        // OPF: copy as-is
        if (nameLow.endsWith(".opf")) {
            File outFile = new File(cacheDir, name);
            outFile.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(outFile);
            StreamUtils.copy(zipInputStream, fos);
            fos.close();
            continue;
        }

        // HTML: process and write with spine index prefix
        if (nameLow.endsWith("html") || nameLow.endsWith("htm") || nameLow.endsWith("xml")) {
            String chId = null;
            for (String key : manifest.keySet()) {
                if (name.contains(key)) {
                    chId = manifest.get(key);
                    break;
                }
            }
            int spineIndex = (chId != null) ? spine.indexOf(chId) : -1;

            ByteArrayOutputStream hStream = new ByteArrayOutputStream();
            Fb2Extractor.generateHyphenFileEpub(
                new InputStreamReader(zipInputStream),
                notes, hStream, name, svgs, spineIndex >= 0 ? spineIndex + 1 : 0, replacements
            );

            String outName = (spineIndex >= 0)
                ? String.format(Locale.US, "spine_%04d_%s", spineIndex, name.replace('/', '_'))
                : name.replace('/', '_');
            File outFile = new File(cacheDir, outName);
            outFile.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(outFile);
            fos.write(hStream.toByteArray());
            fos.close();
        }
    }
    zipInputStream.close();
}
```

**Step 2: Handle imports**

Ensure these imports exist at the top of `EpubExtractor.java`:

```java
import java.io.FileOutputStream;
import com.foobnix.android.utils.StreamUtils;
```

If `StreamUtils` does not exist, use this inline copy helper instead of the `StreamUtils.copy` call:

```java
private static void copyStream(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[16 * 1024];
    int len;
    while ((len = in.read(buffer)) != -1) {
        out.write(buffer, 0, len);
    }
}
```

**Step 3: Verify compilation**

```bash
./gradlew :app:compileFdroidDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL (or pre-existing errors only).

**Step 4: Commit**

```bash
git add app/src/main/java/com/foobnix/ext/EpubExtractor.java
git commit -m "feat: add spine-by-spine cache extraction for EPUB"
```

---

## Task 3: Teach ImageExtractor to prefer spine cache directory

**Objective:** When opening an EPUB, check if a split spine cache exists. If yes, pass the **directory path** to MuPDF instead of the original file path.

**Files:**
- Modify: `app/src/main/java/com/foobnix/sys/ImageExtractor.java:194`

**Step 1: Locate `getNewCodecContext()`**

Find the method signature:

```java
public static synchronized CodecDocument getNewCodecContext(final String path, String ...
```

**Step 2: Add cache check at the top of the method**

```java
public static synchronized CodecDocument getNewCodecContext(final String path, String password, int w, int h) {
    LOG.d("getNewCodecContext new", path, w, h);

    // --- Spine cache shortcut for EPUB ---
    String effectivePath = path;
    File originalFile = new File(path);
    if (BookType.EPUB.is(path) && CacheZipUtils.hasSpineCache(originalFile)) {
        File cacheDir = CacheZipUtils.getSpineCacheDir(originalFile);
        effectivePath = cacheDir.getAbsolutePath();
        LOG.d("getNewCodecContext using spine cache", effectivePath);
    }
    // --------------------------------------

    // ... rest of existing method, replace all subsequent uses of `path` with `effectivePath`
    // when calling codec context open operations.
```

**Note:** Be careful — `path` is still needed for cache key generation (`path.hashCode()` etc). Only the **document open** call should use `effectivePath`. Search the method body for where `path` is passed to native open calls and switch those to `effectivePath`.

**Step 3: Verify compilation**

```bash
./gradlew :app:compileFdroidDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add app/src/main/java/com/foobnix/sys/ImageExtractor.java
git commit -m "feat: use split spine cache directory when opening EPUB"
```

---

## Task 4: Trigger spine cache creation in HorizontalViewActivity

**Objective:** If no cache exists yet, fire a background task to create it *after* the first page is already on screen.

**Files:**
- Modify: `app/src/main/java/com/foobnix/pdf/search/activity/HorizontalViewActivity.java:1762`

**Step 1: Find `onCreate()` / controller init**

Look for the `dc = new HorizontalModeController(...)` block inside `onCreate()` (around line 1762).

**Step 2: Insert cache-warming task after controller init**

Immediately after `dc` is assigned and `viewPager` adapter is set, add:

```java
// Warm spine cache in background if missing
final String bookPath = dc.getBookPath();
if (BookType.EPUB.is(bookPath)) {
    final File bookFile = new File(bookPath);
    if (!CacheZipUtils.hasSpineCache(bookFile)) {
        new CopyAsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    File cacheDir = CacheZipUtils.getSpineCacheDir(bookFile);
                    EpubExtractor.extractSpineToCache(bookPath, cacheDir);
                } catch (Exception e) {
                    LOG.e(e);
                }
                return null;
            }
        }.execute();
    }
}
```

**Step 3: Add import if missing**

```java
import org.ebookdroid.BookType;
import java.io.File;
```

**Step 4: Verify compilation**

```bash
./gradlew :app:compileFdroidDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL.

**Step 5: Commit**

```bash
git add app/src/main/java/com/foobnix/pdf/search/activity/HorizontalViewActivity.java
git commit -m "feat: background spine cache warming on EPUB open"
```

---

## Task 5: Generate a rewritten OPF for the cache directory

**Objective:** MuPDF opens the cache directory as an EPUB archive. It expects `META-INF/container.xml` and `OEBPS/content.opf`. The current Task 2 writes the original OPF, but the spine hrefs now point to renamed files (`spine_0001_...`). We must rewrite the OPF to match.

**Files:**
- Modify: `app/src/main/java/com/foobnix/ext/EpubExtractor.java`

**Step 1: After writing all spine files, rewrite OPF**

In `extractSpineToCache()`, after the second pass loop ends and before `zipInputStream.close()`, scan the cache directory for `content.opf` (or any `.opf`), load it with Jsoup, rewrite the manifest hrefs and spine itemrefs to point to the `spine_NNNN_...` filenames, then overwrite the OPF file.

Pseudocode:

```java
// Rewrite OPF to point to renamed spine files
File opfFile = findOpfFile(cacheDir); // helper needed
if (opfFile != null && opfFile.exists()) {
    Document opf = Jsoup.parse(opfFile, "UTF-8");
    Elements items = opf.select("manifest item");
    for (Element item : items) {
        String href = item.attr("href");
        String chId = item.attr("id");
        int spineIndex = spine.indexOf(chId);
        if (spineIndex >= 0) {
            String newName = String.format(Locale.US, "spine_%04d_%s", spineIndex, href.replace('/', '_'));
            item.attr("href", newName);
        }
    }
    // Write back
    FileOutputStream fos = new FileOutputStream(opfFile);
    fos.write(opf.toString().getBytes("UTF-8"));
    fos.close();
}
```

**Step 2: Implement `findOpfFile()`**

```java
private static File findOpfFile(File cacheDir) {
    File metaInf = new File(cacheDir, "META-INF");
    if (metaInf.exists()) {
        File[] files = metaInf.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().toLowerCase(Locale.US).endsWith(".opf")) {
                    return f;
                }
            }
        }
    }
    return null;
}
```

**Step 3: Verify compilation**

```bash
./gradlew :app:compileFdroidDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add app/src/main/java/com/foobnix/ext/EpubExtractor.java
git commit -m "feat: rewrite OPF manifest to match renamed spine cache files"
```

---

## Task 6: Manual integration test

**Objective:** Prove the cache is created and reused.

**Step 1: Build and install**

```bash
./gradlew :app:assembleFdroidDebug
adb install -r app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
```

**Step 2: Open an EPUB for the first time**

Observe: book opens (may still take time because MuPDF layout is unchanged). Check logcat for:

```
getNewCodecContext using spine cache /.../Book/{hash}_spine
```

If this line does **not** appear, the cache was not found. If it **does** appear but the book still took time, the preprocessing bottleneck is eliminated — any remaining delay is MuPDF layout.

**Step 3: Check cache directory created**

```bash
adb shell ls /sdcard/Android/data/com.foobnix.pdf.reader/cache/Book/
```

Expected: a directory named `{filenameHash}_spine` containing `spine_0001_...`, `spine_0002_...`, `META-INF/`, and CSS/image resources.

**Step 4: Re-open the same EPUB**

Observe: the logcat line `getNewCodecContext using spine cache` **must** appear this time, and the open should be faster than the first time.

**Step 5: Commit test notes**

```bash
git add docs/plans/
git commit -m "docs: mark spine cache plan as implemented"
```

---

## Task 7: Cleanup — remove the original loading dialog if appropriate

**Objective:** Since the preprocessing step is now cached, the `Dialogs.loadingBook()` dialog may be less necessary. Evaluate whether to keep it as a placeholder or remove it for EPUB files when cache is warm.

**Files:**
- Review: `app/src/main/java/com/foobnix/pdf/search/activity/HorizontalViewActivity.java` (search for `Dialogs.loadingBook`)

**Decision point:** If the remaining delay (MuPDF layout of the first chapter) is under ~500ms on target device, remove the loading dialog for warm-cache EPUB opens. If still over 500ms, keep it but change the message to "Rendering...".

Defer this decision until Task 6 timing data is collected.

---

## Known Limitations (documented)

1. **MuPDF layout time remains.** This plan removes Java preprocessing (ZIP extraction + HTML hyphenation) from the critical path. MuPDF still runs full HTML/CSS layout when `getPageCount()` is called. Fixing that requires C++ changes (deferred).
2. **Single-giant-HTML EPUBs.** If an EPUB contains one enormous HTML file (100+ MB), this plan does not split it further. Recommendation: convert such books to TXT.
3. **Cache invalidation.** If BookCSS settings (font size, margins, hyphenation language) change, the cached HTML may be stale. A robust invalidation key (hash of BookCSS + file size) should be added later.
4. **No persistent accelerator.** The MuPDF `epub_accelerator` disk save is not implemented here; deferred.

---

## Rollback

If the cache directory causes MuPDF to fail to open the EPUB (directory format not accepted), revert to the original file path by removing the `effectivePath` switch in `ImageExtractor.java`.

---

## End of Plan

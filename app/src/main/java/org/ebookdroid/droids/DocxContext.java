package org.ebookdroid.droids;

import org.ebookdroid.core.codec.CodecDocument;
import org.ebookdroid.droids.mupdf.codec.PdfContext;
import java.io.File;

public class DocxContext extends PdfContext {
    @Override
    public File getCacheFileName(String fileNameOriginal) {
        return new File(fileNameOriginal);
    }

    @Override
    public CodecDocument openDocumentInner(String fileName, String password) {
        return null;
    }
}

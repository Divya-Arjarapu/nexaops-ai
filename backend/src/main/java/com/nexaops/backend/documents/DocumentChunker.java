package com.nexaops.backend.documents;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {

    private final int chunkSize;
    private final int overlap;

    public DocumentChunker(
            @Value("${nexaops.documents.chunk-size}") int chunkSize,
            @Value("${nexaops.documents.chunk-overlap}") int overlap
    ) {
        if (chunkSize < 1 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("Chunk size must be positive and overlap must be smaller than the chunk size.");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<DocumentChunk> chunk(List<ExtractedPage> pages) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (pages == null) return chunks;

        for (ExtractedPage page : pages) {
            if (page == null || page.text() == null || page.text().isBlank()) continue;
            addPageChunks(page, chunks);
        }
        return List.copyOf(chunks);
    }

    private void addPageChunks(ExtractedPage page, List<DocumentChunk> chunks) {
        String text = page.text();
        int start = 0;
        while (start < text.length()) {
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
            if (start >= text.length()) break;

            int end = Math.min(start + chunkSize, text.length());
            if (end < text.length()) {
                int candidate = end;
                while (candidate > start + 1 && !Character.isWhitespace(text.charAt(candidate - 1))) candidate--;
                if (candidate > start + 1) end = candidate - 1;
            }

            int contentEnd = end;
            while (contentEnd > start && Character.isWhitespace(text.charAt(contentEnd - 1))) contentEnd--;
            if (contentEnd > start) {
                int chunkIndex = chunks.size();
                String locator = page.pageNumber() == null
                        ? "Text characters " + start + "-" + contentEnd
                        : "Page " + page.pageNumber();
                chunks.add(new DocumentChunk(
                        chunkIndex,
                        text.substring(start, contentEnd),
                        page.pageNumber(),
                        locator,
                        start,
                        contentEnd
                ));
            }

            if (end >= text.length()) break;
            int nextStart = Math.max(start + 1, end - overlap);
            while (nextStart < text.length() && !Character.isWhitespace(text.charAt(nextStart))) nextStart++;
            while (nextStart < text.length() && Character.isWhitespace(text.charAt(nextStart))) nextStart++;
            start = nextStart;
        }
    }
}

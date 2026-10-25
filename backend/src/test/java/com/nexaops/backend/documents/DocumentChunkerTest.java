package com.nexaops.backend.documents;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentChunkerTest {

    @Test
    void keepsChunksOrderedBoundedAndAttachedToTheirSourcePages() {
        DocumentChunker chunker = new DocumentChunker(12, 3);
        String firstPage = "alpha beta gamma delta";
        String secondPage = "epsilon zeta eta theta";

        List<DocumentChunk> chunks = chunker.chunk(List.of(
                new ExtractedPage(1, firstPage),
                new ExtractedPage(2, secondPage)
        ));

        assertTrue(chunks.size() > 2);
        assertEquals(IntStream.range(0, chunks.size()).boxed().toList(),
                chunks.stream().map(DocumentChunk::chunkIndex).toList());
        assertTrue(chunks.stream().allMatch(chunk -> !chunk.content().isBlank() && chunk.content().length() <= 12));
        assertTrue(chunks.stream().filter(chunk -> chunk.pageNumber() == 1)
                .allMatch(chunk -> firstPage.substring(chunk.sourceStartOffset(), chunk.sourceEndOffset())
                        .equals(chunk.content())));
        assertTrue(chunks.stream().filter(chunk -> chunk.pageNumber() == 2)
                .allMatch(chunk -> secondPage.substring(chunk.sourceStartOffset(), chunk.sourceEndOffset())
                        .equals(chunk.content())));
        assertEquals("Page 2", chunks.stream()
                .filter(chunk -> chunk.pageNumber() == 2)
                .findFirst()
                .orElseThrow()
                .sourceLocator());
    }

    @Test
    void skipsWhitespacePagesAndUsesTextCharacterLocators() {
        DocumentChunker chunker = new DocumentChunker(10, 2);

        List<DocumentChunk> chunks = chunker.chunk(List.of(
                new ExtractedPage(null, " \n "),
                new ExtractedPage(null, "alpha beta gamma")
        ));

        assertTrue(chunks.stream().allMatch(chunk -> chunk.pageNumber() == null));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.sourceLocator().startsWith("Text characters ")));
        assertTrue(chunks.stream().noneMatch(chunk -> chunk.content().isBlank()));
    }
}

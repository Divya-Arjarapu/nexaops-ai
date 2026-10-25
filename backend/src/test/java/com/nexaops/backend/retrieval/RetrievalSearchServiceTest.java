package com.nexaops.backend.retrieval;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalSearchServiceTest {

    private final PythonRetrievalClient client = mock(PythonRetrievalClient.class);
    private final RetrievalSearchService service = new RetrievalSearchService(client, 2000, 5, 10);

    @Test
    void returnsTypedRetrievedSourcesAndEmptyResults() {
        RetrievalSearchResult source = new RetrievalSearchResult(
                "document-1", "operations.pdf", "42", 0, 2, "Access", "Page 2",
                "Administrator approval is required.", 0.87
        );
        when(client.search("Who approves production access?", 5)).thenReturn(new RetrievalSearchResponse(
                "Who approves production access?", List.of(source), 1
        ));

        RetrievalSearchResponse response = service.search(new RetrievalSearchRequest(
                "  Who approves production access?  ", null
        ));

        assertEquals(1, response.resultCount());
        assertEquals("operations.pdf", response.results().getFirst().filename());
        assertEquals(2, response.results().getFirst().pageNumber());
        assertEquals(0.87, response.results().getFirst().similarityScore());

        when(client.search("unmatched", 2)).thenReturn(new RetrievalSearchResponse("unmatched", List.of(), 0));
        assertEquals(0, service.search(new RetrievalSearchRequest("unmatched", 2)).resultCount());
    }

    @Test
    void rejectsEmptyLongAndOutOfRangeRequestsBeforeCallingPython() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(RetrievalException.class,
                () -> service.search(new RetrievalSearchRequest("  ", null))).status());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(RetrievalException.class,
                () -> service.search(new RetrievalSearchRequest("x".repeat(2001), null))).status());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(RetrievalException.class,
                () -> service.search(new RetrievalSearchRequest("valid", 0))).status());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(RetrievalException.class,
                () -> service.search(new RetrievalSearchRequest("valid", 11))).status());
        verify(client, never()).search(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }
}

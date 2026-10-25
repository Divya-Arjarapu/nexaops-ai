package com.nexaops.backend.rag;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagAnswerServiceTest {

    private final PythonRagClient client = mock(PythonRagClient.class);
    private final RagAnswerService service = new RagAnswerService(client, 2000, 5, 10);

    @Test
    void trimsQuestionAndUsesDefaultTopK() {
        RagAnswerResponse expected = new RagAnswerResponse("Who approves access?", "An admin [S1].", true,
                java.util.List.of(), java.util.List.of());
        when(client.answer("Who approves access?", 5)).thenReturn(expected);
        assertEquals(expected, service.answer(new RagAnswerRequest(" Who approves access? ", null)));
    }

    @Test
    void rejectsEmptyLongAndOutOfRangeQuestionsBeforeCallingPython() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(RagException.class,
                () -> service.answer(new RagAnswerRequest("  ", null))).status());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(RagException.class,
                () -> service.answer(new RagAnswerRequest("x".repeat(2001), null))).status());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(RagException.class,
                () -> service.answer(new RagAnswerRequest("valid", 0))).status());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(RagException.class,
                () -> service.answer(new RagAnswerRequest("valid", 11))).status());
        verify(client, never()).answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }
}

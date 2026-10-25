package com.nexaops.backend.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ManagedDocument(
        UUID documentId,
        String filename,
        OffsetDateTime uploadedAt,
        String status,
        int chunkCount,
        int embeddedChunkCount
) { }

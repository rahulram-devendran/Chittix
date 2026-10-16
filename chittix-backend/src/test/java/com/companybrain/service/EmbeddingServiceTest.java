package com.companybrain.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    EmbeddingModel mockModel;

    @Test
    void rejectsNullOrBlankInput() {
        EmbeddingService service = new EmbeddingService(mockModel);

        assertThrows(IllegalArgumentException.class, () -> service.embed(null));
        assertThrows(IllegalArgumentException.class, () -> service.embed(""));
        assertThrows(IllegalArgumentException.class, () -> service.embed("   \t\n  "));
    }

    @Test
    void returnsValid384DimensionalVectorWhenModelSucceeds() {
        float[] sampleVector = new float[384];
        for (int i = 0; i < 384; i++) {
            sampleVector[i] = 0.05f;
        }
        when(mockModel.embed(anyString())).thenReturn(Response.from(Embedding.from(sampleVector)));

        EmbeddingService service = new EmbeddingService(mockModel);
        List<Float> result = service.embed("PostgreSQL transactional guarantees");

        assertNotNull(result);
        assertEquals(384, result.size());
        for (float v : result) {
            assertFalse(Float.isNaN(v));
            assertFalse(Float.isInfinite(v));
        }
    }

    @Test
    void rejectsModelOutputWithMismatchedDimension() {
        float[] wrongVector = new float[512];
        when(mockModel.embed(anyString())).thenReturn(Response.from(Embedding.from(wrongVector)));

        EmbeddingService service = new EmbeddingService(mockModel);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.embed("sample"));
        assertTrue(ex.getMessage().contains("Expected 384 dimensions"));
    }

    @Test
    void rejectsNonFiniteValuesInVector() {
        float[] invalidVector = new float[384];
        invalidVector[10] = Float.NaN;
        when(mockModel.embed(anyString())).thenReturn(Response.from(Embedding.from(invalidVector)));

        EmbeddingService service = new EmbeddingService(mockModel);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.embed("sample"));
        assertTrue(ex.getMessage().contains("non-finite"));
    }

    @Test
    void wrapsModelExceptionsGracefully() {
        when(mockModel.embed(anyString())).thenThrow(new RuntimeException("ONNX Runtime engine error"));

        EmbeddingService service = new EmbeddingService(mockModel);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.embed("sample"));
        assertTrue(ex.getMessage().contains("Failed to generate embedding"));
    }

    @Test
    void realModelGeneratesValid384Embedding() {
        // Verifies the bundled quantized all-MiniLM-L6-v2 ONNX model executes on the local JVM
        EmbeddingService service = new EmbeddingService();
        List<Float> embedding = service.embed("Payment Service PostgreSQL architecture decision");

        assertNotNull(embedding);
        assertEquals(384, embedding.size());
        for (Float val : embedding) {
            assertNotNull(val);
            assertFalse(val.isNaN());
            assertFalse(val.isInfinite());
        }
    }
}

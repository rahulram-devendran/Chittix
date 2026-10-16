package com.companybrain.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating local 384-dimensional text embeddings using the
 * in-process quantized all-MiniLM-L6-v2 ONNX model.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    public static final int EXPECTED_DIMENSION = 384;

    private final EmbeddingModel embeddingModel;

    public EmbeddingService() {
        this(new AllMiniLmL6V2QuantizedEmbeddingModel());
    }

    public EmbeddingService(EmbeddingModel embeddingModel) {
        if (embeddingModel == null) {
            throw new IllegalArgumentException("embeddingModel must not be null");
        }
        this.embeddingModel = embeddingModel;
    }

    public int getDimension() {
        return EXPECTED_DIMENSION;
    }

    /**
     * Generates a finite, non-empty 384-dimensional vector embedding for the given text.
     *
     * @param text input text to embed (must not be null or blank)
     * @return 384-dimensional List of Float
     */
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to embed must not be blank");
        }

        Response<Embedding> response;
        try {
            response = embeddingModel.embed(text.trim());
        } catch (Exception e) {
            log.error("Embedding model execution failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to generate embedding: " + e.getMessage(), e);
        }

        if (response == null || response.content() == null) {
            throw new IllegalStateException("Embedding model returned null response/content");
        }

        Embedding embedding = response.content();
        float[] vector = embedding.vector();

        if (vector == null || vector.length != EXPECTED_DIMENSION) {
            int actualDim = vector == null ? 0 : vector.length;
            throw new IllegalStateException("Expected " + EXPECTED_DIMENSION + " dimensions, but model produced " + actualDim);
        }

        List<Float> list = new ArrayList<>(EXPECTED_DIMENSION);
        for (float v : vector) {
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                throw new IllegalStateException("Embedding vector contains non-finite value: " + v);
            }
            list.add(v);
        }

        return list;
    }
}

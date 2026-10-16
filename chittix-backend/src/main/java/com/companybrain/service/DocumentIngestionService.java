package com.companybrain.service;

import com.companybrain.dto.DocumentIngestionResult;
import com.companybrain.dto.TextChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for ingesting real document text into FalkorDB as connected (:DocumentChunk) nodes
 * with native 384-dimensional vector embeddings.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final GraphClient graphClient;
    private final VectorSchemaService vectorSchemaService;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;

    public DocumentIngestionService(GraphClient graphClient,
                                    VectorSchemaService vectorSchemaService,
                                    TextChunker textChunker,
                                    EmbeddingService embeddingService) {
        this.graphClient = graphClient;
        this.vectorSchemaService = vectorSchemaService;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
    }

    /**
     * Explicitly registers a new Document node in the graph (e.g. for testing or controlled registration).
     */
    public void registerDocument(String sourceId, String title) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        String cypher = "MERGE (doc:Document {source_id: $sourceId}) ON CREATE SET doc.title = $title ON MATCH SET doc.title = $title";
        graphClient.executeWrite(cypher, Map.of("sourceId", sourceId.trim(), "title", title.trim()));
    }

    /**
     * Ingests document text for an existing Document node:
     * 1. Validates that the Document node exists in the graph.
     * 2. Safely verifies/initializes the vector index with dimension 384.
     * 3. Chunks the document text.
     * 4. Embeds each chunk into a 384-dimensional vector.
     * 5. Uses parameterized Cypher and MERGE with stable chunk IDs to store (:DocumentChunk) nodes and (:HAS_CHUNK) relationships.
     * 6. Re-ingestion idempotently updates existing chunks and prunes any stale higher-index chunks.
     *
     * @param sourceId document source_id (must match an existing (:Document) node in FalkorDB)
     * @param content  document text content
     * @return DocumentIngestionResult
     */
    public DocumentIngestionResult ingestDocument(String sourceId, String content) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }

        String sid = sourceId.trim();

        // 1. Check existing source document in FalkorDB
        String checkDocCypher = "MATCH (doc:Document {source_id: $sourceId}) RETURN doc.title AS title";
        List<Map<String, Object>> docRows = graphClient.query(checkDocCypher, Map.of("sourceId", sid));
        if (docRows.isEmpty()) {
            throw new IllegalArgumentException("Source document not found in graph for source_id: " + sid);
        }
        String documentTitle = String.valueOf(docRows.get(0).get("title"));

        // 2. Controlled vector index initialization & verification
        ensureVectorIndexReady();

        // 3. Chunk text
        List<TextChunk> chunks = textChunker.chunk(content);
        if (chunks.isEmpty()) {
            return DocumentIngestionResult.skippedEmpty(sid, documentTitle);
        }

        // 4. Store each chunk via parameterized Cypher and MERGE
        String chunkUpsertCypher = """
                MATCH (doc:Document {source_id: $sourceId})
                MERGE (c:DocumentChunk {id: $chunkId})
                ON CREATE SET
                  c.index = $chunkIndex,
                  c.content = $content,
                  c.source_id = $sourceId,
                  c.embedding = vecf32($embedding)
                ON MATCH SET
                  c.index = $chunkIndex,
                  c.content = $content,
                  c.source_id = $sourceId,
                  c.embedding = vecf32($embedding)
                MERGE (doc)-[:HAS_CHUNK]->(c)
                """;

        for (TextChunk chunk : chunks) {
            String chunkId = sid + "#chunk-" + chunk.index();
            List<Float> embedding = embeddingService.embed(chunk.content());

            Map<String, Object> params = Map.of(
                    "sourceId", sid,
                    "chunkId", chunkId,
                    "chunkIndex", chunk.index(),
                    "content", chunk.content(),
                    "embedding", embedding
            );
            graphClient.executeWrite(chunkUpsertCypher, params);
        }

        // 5. If re-ingestion produced fewer chunks than a prior run, prune any stale chunks
        String pruneCypher = """
                MATCH (doc:Document {source_id: $sourceId})-[:HAS_CHUNK]->(c:DocumentChunk)
                WHERE c.index >= $newChunkCount
                DETACH DELETE c
                """;
        graphClient.executeWrite(pruneCypher, Map.of("sourceId", sid, "newChunkCount", chunks.size()));

        log.info("Successfully ingested {} chunk(s) for document '{}' ({})", chunks.size(), documentTitle, sid);
        return DocumentIngestionResult.success(sid, documentTitle, chunks.size());
    }

    /**
     * Safely ensures the vector index is operational with the required dimension (384).
     * If an index exists with a mismatched dimension, fails safely without dropping or modifying.
     */
    public void ensureVectorIndexReady() {
        if (!vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")) {
            vectorSchemaService.initializeVectorIndex(384, "cosine", "DocumentChunk", "embedding");
        } else {
            Integer existingDim = vectorSchemaService.getExistingVectorIndexDimension("DocumentChunk", "embedding");
            if (existingDim != null && existingDim != 384) {
                throw new IllegalStateException("Vector index on :DocumentChunk(embedding) already exists with dimension "
                        + existingDim + ", but required dimension is 384. Mismatch detected; manual resolution required.");
            }
        }
    }
}

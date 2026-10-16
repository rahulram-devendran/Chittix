package com.companybrain.service;

import com.companybrain.config.VectorSchemaProperties;
import com.companybrain.dto.DocumentIngestionResult;
import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test running against an isolated temporary graph in FalkorDB (temp_ingest_test_graph).
 * Verifies end-to-end vector index creation, chunk embedding, MERGE idempotency, and graph storage.
 * Does not touch company_brain.
 */
class DocumentIngestionIntegrationTest {

    private static final String TEMP_GRAPH = "temp_ingest_test_graph";

    private Driver driver;
    private GraphClient testGraphClient;
    private DocumentIngestionService ingestionService;
    private VectorSchemaService schemaService;

    @BeforeEach
    void setUp() {
        try {
            driver = FalkorDB.driver("localhost", 6379);
            // Verify reachability
            driver.graph(TEMP_GRAPH).query("RETURN 1");
        } catch (Exception e) {
            assumeTrue(false, "FalkorDB is not reachable on localhost:6379; skipping integration test: " + e.getMessage());
        }

        testGraphClient = new GraphClient(driver, TEMP_GRAPH);
        VectorSchemaProperties properties = new VectorSchemaProperties(384, "cosine", "DocumentChunk", "embedding");
        schemaService = new VectorSchemaService(testGraphClient, properties);
        TextChunker chunker = new TextChunker(150, 30);
        EmbeddingService embeddingService = new EmbeddingService();

        ingestionService = new DocumentIngestionService(testGraphClient, schemaService, chunker, embeddingService);
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            try {
                driver.graph(TEMP_GRAPH).deleteGraph();
            } catch (Exception ignored) {
            }
            try {
                driver.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void endToEndIngestionStoresConnectedChunksAndIsIdempotent() {
        // 1. Explicitly register test document
        ingestionService.registerDocument("DOC-TEST-001", "Architecture Decision Record - Payment Storage");

        // Verify document node exists
        List<Map<String, Object>> docs = testGraphClient.query(
                "MATCH (d:Document {source_id: 'DOC-TEST-001'}) RETURN d.title AS title",
                Map.of()
        );
        assertEquals(1, docs.size());
        assertEquals("Architecture Decision Record - Payment Storage", docs.get(0).get("title"));

        // 2. Ingest document text
        String docText = "PostgreSQL was selected for the Payment Service because it provides full ACID guarantees, "
                + "robust serializable isolation levels, and mature connection pooling. "
                + "The team considered DynamoDB and MongoDB but prioritized transactional consistency "
                + "for ledger operations and audit tracking.";

        DocumentIngestionResult result = ingestionService.ingestDocument("DOC-TEST-001", docText);

        assertEquals("INGESTED", result.status());
        assertTrue(result.chunkCount() >= 2, "Expected multiple chunks for text, got: " + result.chunkCount());

        // 3. Verify vector index is operational in FalkorDB
        assertTrue(schemaService.isVectorIndexPresent("DocumentChunk", "embedding"));
        assertEquals(384, schemaService.getExistingVectorIndexDimension("DocumentChunk", "embedding"));

        // 4. Verify DocumentChunk nodes and relationships in FalkorDB
        List<Map<String, Object>> chunks = testGraphClient.query(
                "MATCH (d:Document {source_id: 'DOC-TEST-001'})-[:HAS_CHUNK]->(c:DocumentChunk) "
                        + "RETURN c.id AS id, c.index AS index, c.content AS content, c.source_id AS sourceId "
                        + "ORDER BY c.index ASC",
                Map.of()
        );
        assertEquals(result.chunkCount(), chunks.size());
        assertEquals("DOC-TEST-001#chunk-0", chunks.get(0).get("id"));
        assertEquals("DOC-TEST-001", chunks.get(0).get("sourceId"));
        assertNotNull(chunks.get(0).get("content"));

        // 5. Test idempotency: re-ingest with updated text
        String updatedText = "PostgreSQL was selected for Payment Service due to strict ledger ACID compliance. "
                + "Audit logging and connection pooling were key factors.";

        DocumentIngestionResult reingestResult = ingestionService.ingestDocument("DOC-TEST-001", updatedText);
        assertEquals("INGESTED", reingestResult.status());

        // Verify total chunk count matches the updated run and does NOT duplicate
        List<Map<String, Object>> chunksAfterReingest = testGraphClient.query(
                "MATCH (d:Document {source_id: 'DOC-TEST-001'})-[:HAS_CHUNK]->(c:DocumentChunk) "
                        + "RETURN count(c) AS count",
                Map.of()
        );
        assertEquals(reingestResult.chunkCount(), ((Number) chunksAfterReingest.get(0).get("count")).intValue());
    }
}

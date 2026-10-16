package com.companybrain.service;

import com.companybrain.config.VectorSchemaProperties;
import com.companybrain.dto.RetrievedChunk;
import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end integration test verifying native FalkorDB semantic vector retrieval
 * on an isolated temporary graph (temp_retrieval_test_graph).
 * Uses real local all-MiniLM-L6-v2 embeddings and real FalkorDB vector indexing.
 * Does not touch company_brain.
 */
class VectorRetrievalIntegrationTest {

    private static final String TEMP_GRAPH = "temp_retrieval_test_graph";

    private Driver driver;
    private GraphClient testGraphClient;
    private DocumentIngestionService ingestionService;
    private VectorRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        try {
            driver = FalkorDB.driver("localhost", 6379);
            driver.graph(TEMP_GRAPH).query("RETURN 1");
        } catch (Exception e) {
            assumeTrue(false, "FalkorDB is not reachable on localhost:6379; skipping integration test: " + e.getMessage());
        }

        testGraphClient = new GraphClient(driver, TEMP_GRAPH);
        VectorSchemaProperties properties = new VectorSchemaProperties(384, "cosine", "DocumentChunk", "embedding");
        VectorSchemaService schemaService = new VectorSchemaService(testGraphClient, properties);
        TextChunker chunker = new TextChunker(250, 40);
        EmbeddingService embeddingService = new EmbeddingService();

        ingestionService = new DocumentIngestionService(testGraphClient, schemaService, chunker, embeddingService);
        retrievalService = new VectorRetrievalService(testGraphClient, schemaService, embeddingService, properties, null);
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
    void retrievesSemanticallyRelevantChunksAcrossMultipleDocumentsWithCorrectScoreSemantics() {
        // 1. Ingest Payment Architecture document
        ingestionService.registerDocument("DOC-PAY-001", "Payment Architecture Design");
        String paymentDocText = "PostgreSQL was selected for the Payment Service because it guarantees strict ACID compliance, "
                + "serializable transaction isolation levels, and immutable audit logs. "
                + "High-throughput financial ledgers require relational consistency and battle-tested foreign keys.";
        ingestionService.ingestDocument("DOC-PAY-001", paymentDocText);

        // 2. Ingest Vacation Policy document
        ingestionService.registerDocument("DOC-HR-002", "Employee Vacation Policy");
        String hrDocText = "Full-time employees receive twenty days of paid time off per calendar year. "
                + "Vacation requests should be submitted through the HR portal at least two weeks in advance. "
                + "Unused PTO rolls over up to a maximum cap of five days.";
        ingestionService.ingestDocument("DOC-HR-002", hrDocText);

        // 3. Query related to payments & database selection
        List<RetrievedChunk> paymentResults = retrievalService.retrieve("Why did we choose PostgreSQL for financial transactions?", 3);

        assertFalse(paymentResults.isEmpty(), "Expected retrieved chunks for payment query");
        RetrievedChunk topPayment = paymentResults.get(0);

        assertEquals("DOC-PAY-001", topPayment.sourceId());
        assertEquals("Payment Architecture Design", topPayment.documentTitle());
        assertTrue(topPayment.content().toLowerCase().contains("postgresql"));
        // Distance score should be low (< 0.60) indicating strong semantic relevance
        assertTrue(topPayment.score() < 0.60, "Expected low cosine distance for relevant chunk: " + topPayment.score());
        assertTrue(topPayment.similarityScore() > 0.40);

        // Verify result ordering: each result must have score <= next result
        for (int i = 0; i < paymentResults.size() - 1; i++) {
            assertTrue(paymentResults.get(i).score() <= paymentResults.get(i + 1).score(),
                    "Results must be in ascending order of distance score");
        }

        // 4. Query related to HR & time off
        List<RetrievedChunk> hrResults = retrievalService.retrieve("How many days of paid time off do employees get?", 3);

        assertFalse(hrResults.isEmpty(), "Expected retrieved chunks for HR query");
        RetrievedChunk topHr = hrResults.get(0);

        assertEquals("DOC-HR-002", topHr.sourceId());
        assertEquals("Employee Vacation Policy", topHr.documentTitle());
        assertTrue(topHr.content().toLowerCase().contains("paid time off"));
        assertTrue(topHr.score() < 0.60, "Expected low cosine distance for relevant HR chunk: " + topHr.score());
    }
}

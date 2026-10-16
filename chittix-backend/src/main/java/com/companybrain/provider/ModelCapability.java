package com.companybrain.provider;

/**
 * Declares capabilities supported by various LLM providers and models.
 * Used for capability negotiation and feature validation.
 */
public enum ModelCapability {
    /**
     * Standard conversational text completion.
     */
    CHAT,

    /**
     * Runs completely locally on the host machine without network connectivity.
     */
    LOCAL,

    /**
     * Supports token-by-token HTTP streaming.
     */
    STREAMING,

    /**
     * Guarantees strict JSON Schema / structured output adherence.
     */
    STRUCTURED_OUTPUT,

    /**
     * Supports native function calling and tool execution.
     */
    TOOL_CALLING,

    /**
     * Accepts multimodal inputs such as images, audio, or PDFs.
     */
    MULTIMODAL
}

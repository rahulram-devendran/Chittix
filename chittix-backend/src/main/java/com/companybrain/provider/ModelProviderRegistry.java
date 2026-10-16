package com.companybrain.provider;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry holding all available LLM provider adapters.
 * Enables provider discovery and selection without hardcoded vendor branching.
 */
@Component
public class ModelProviderRegistry {

    private final Map<ModelProvider, ModelProviderAdapter> adapterMap = new EnumMap<>(ModelProvider.class);

    public ModelProviderRegistry(List<ModelProviderAdapter> adapters) {
        if (adapters != null) {
            for (ModelProviderAdapter adapter : adapters) {
                adapterMap.put(adapter.getProvider(), adapter);
            }
        }
    }

    /**
     * Finds the adapter corresponding to the specified provider.
     */
    public Optional<ModelProviderAdapter> findAdapter(ModelProvider provider) {
        if (provider == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(adapterMap.get(provider));
    }

    /**
     * Returns the adapter or throws IllegalArgumentException if not found.
     */
    public ModelProviderAdapter getAdapter(ModelProvider provider) {
        return findAdapter(provider)
                .orElseThrow(() -> new IllegalArgumentException("No adapter registered for provider: " + provider));
    }

    /**
     * Checks if a provider has a registered adapter.
     */
    public boolean isRegistered(ModelProvider provider) {
        return adapterMap.containsKey(provider);
    }

    /**
     * Returns all registered providers.
     */
    public Set<ModelProvider> getRegisteredProviders() {
        return Collections.unmodifiableSet(adapterMap.keySet());
    }
}

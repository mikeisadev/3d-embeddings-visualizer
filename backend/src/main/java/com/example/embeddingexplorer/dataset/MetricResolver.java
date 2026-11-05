package com.example.embeddingexplorer.dataset;

import com.example.embeddingexplorer.config.ExplorerProperties;
import com.example.embeddingexplorer.config.TableRef;
import org.springframework.stereotype.Component;

/**
 * The distance metric in use right now, for queries that do not need the whole snapshot
 * (neighbors, comparisons, RAG search): one catalog query.
 */
@Component
public class MetricResolver {

    private final TableInspector inspector;
    private final TableRef table;
    private final ExplorerProperties properties;

    public MetricResolver(TableInspector inspector, TableRef table, ExplorerProperties properties) {
        this.inspector = inspector;
        this.table = table;
        this.properties = properties;
    }

    public ResolvedMetric current() {
        return ResolvedMetric.resolve(properties.distanceMetric(), inspector.embeddingIndexes(table));
    }
}

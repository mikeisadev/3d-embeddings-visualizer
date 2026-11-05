package com.example.embeddingexplorer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of Embedding Explorer 3D, a read-only teaching tool that shows the pgvector
 * embeddings of the course chatbot as a 3D point cloud.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EmbeddingExplorerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmbeddingExplorerApplication.class, args);
    }
}

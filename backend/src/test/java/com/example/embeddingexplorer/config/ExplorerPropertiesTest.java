package com.example.embeddingexplorer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class ExplorerPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues(
                    "viz.db.url=jdbc:postgresql://localhost:5432/chatbot",
                    "viz.db.user=chatbot",
                    "viz.db.password=chatbot123",
                    "viz.db.schema=public",
                    "viz.db.table=vector_store",
                    "viz.db.statement-timeout=15s",
                    "viz.db.connection-timeout=5s",
                    "viz.distance-metric=auto",
                    "viz.max-points=5000",
                    "viz.snapshot-memory-budget-mb=256",
                    "viz.embedding.enabled=false",
                    "viz.embedding.base-url=https://api.openai.com/v1",
                    "viz.embedding.model=text-embedding-3-small",
                    "viz.embedding.api-key=",
                    "viz.search-rate-limit-per-minute=10");

    @Test
    void bindsValidConfiguration() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            ExplorerProperties properties = context.getBean(ExplorerProperties.class);
            assertThat(properties.db().tableRef().displayName()).isEqualTo("public.vector_store");
            assertThat(properties.db().statementTimeout()).isEqualTo(Duration.ofSeconds(15));
            assertThat(properties.distanceMetric()).isEqualTo(DistanceMetricSetting.AUTO);
            assertThat(properties.embedding().hasApiKey()).isFalse();
        });
    }

    @Test
    void bindsTheMetricLikeTheEnvironmentVariableIsWritten() {
        runner.withPropertyValues("viz.distance-metric=inner_product").run(context -> assertThat(
                        context.getBean(ExplorerProperties.class).distanceMetric())
                .isEqualTo(DistanceMetricSetting.INNER_PRODUCT));
    }

    @Test
    void rejectsAnIllegalTableNameAtStartup() {
        runner.withPropertyValues("viz.db.table=vector_store; DROP TABLE x").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("viz.db.table");
        });
    }

    @Test
    void rejectsANonPostgresUrl() {
        runner.withPropertyValues("viz.db.url=jdbc:mysql://localhost/chatbot").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("viz.db.url");
        });
    }

    @Test
    void rejectsAnUnknownMetric() {
        runner.withPropertyValues("viz.distance-metric=manhattan")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsOutOfRangeLimits() {
        runner.withPropertyValues("viz.max-points=1").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("viz.max-points");
        });
    }

    @Test
    void neverPrintsSecrets() {
        runner.withPropertyValues("viz.embedding.api-key=sk-secret-value").run(context -> {
            ExplorerProperties properties = context.getBean(ExplorerProperties.class);
            assertThat(properties.toString()).doesNotContain("chatbot123").doesNotContain("sk-secret-value");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ExplorerProperties.class)
    static class PropertiesConfig {}
}

package co.com.pragma.metrics.aws;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.metrics.MetricCategory;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricLevel;
import software.amazon.awssdk.metrics.MetricRecord;
import software.amazon.awssdk.metrics.SdkMetric;
import software.amazon.awssdk.metrics.internal.DefaultMetricCollection;
import software.amazon.awssdk.metrics.internal.DefaultMetricRecord;
import software.amazon.awssdk.metrics.internal.EmptyMetricCollection;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MicrometerMetricPublisherTest {

    @Test
    void publishesTimersCountersAndTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerMetricPublisher publisher = new MicrometerMetricPublisher(registry);
        SdkMetric<String> service = metric("Service", String.class);
        SdkMetric<Boolean> success = metric("Success", Boolean.class);
        SdkMetric<Duration> duration = metric("ApiCallDuration", Duration.class);
        SdkMetric<Integer> attempts = metric("RetryCount", Integer.class);
        SdkMetric<Double> unsupported = metric("PayloadSize", Double.class);

        publisher.publish(collection(
                record(service, "franchise"),
                record(success, true),
                record(duration, Duration.ofMillis(25)),
                record(attempts, 3),
                record(unsupported, 10.5)));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertEquals(1, registry.find("ApiCallDuration")
                    .tags("Service", "franchise", "Success", "true").timer().count());
            assertEquals(3, registry.find("RetryCount")
                    .tags("Service", "franchise", "Success", "true").counter().count());
            assertNull(registry.find("PayloadSize").meter());
        });
        publisher.close();
    }

    @Test
    void acceptsEmptyCollectionsAndClose() {
        MicrometerMetricPublisher publisher = new MicrometerMetricPublisher(new SimpleMeterRegistry());

        publisher.publish(EmptyMetricCollection.create());
        publisher.close();
    }

    private <T> SdkMetric<T> metric(String name, Class<T> type) {
        return SdkMetric.create(name, type, MetricLevel.INFO, MetricCategory.CORE);
    }

    private <T> MetricRecord<T> record(SdkMetric<T> metric, T value) {
        return new DefaultMetricRecord<>(metric, value);
    }

    @SafeVarargs
    private final MetricCollection collection(MetricRecord<?>... records) {
        Map<SdkMetric<?>, List<MetricRecord<?>>> metrics = new LinkedHashMap<>();
        for (MetricRecord<?> record : records) {
            metrics.computeIfAbsent(record.metric(), ignored -> new java.util.ArrayList<>()).add(record);
        }
        return new DefaultMetricCollection("test", metrics, List.of());
    }

}

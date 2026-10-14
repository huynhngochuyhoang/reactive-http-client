package evidence.v36;

import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.runner.IterationType;
import org.openjdk.jmh.runner.WorkloadParams;
import org.openjdk.jmh.runner.options.TimeValue;

import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Deterministic callback test; no JMH runner, measured loop or workload. */
public final class BoundaryRecorderTest {
    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("v36-recorder-test-");
        var output = directory.resolve("boundaries.xml");
        try {
            System.setProperty("v36.stage", "synthetic-stage");
            System.setProperty("v36.seal", "synthetic-seal");
            System.setProperty("v36.boundaries", output.toString());
            var warmup = new IterationParams(IterationType.WARMUP, 5, TimeValue.seconds(1), 1);
            var measurement = new IterationParams(IterationType.MEASUREMENT, 5, TimeValue.seconds(1), 1);
            var values = new WorkloadParams();
            values.put("profile", "HOOK", 0);
            values.put("scenario", "GET", 0);
            var benchmark = new BenchmarkParams("synthetic", "synthetic", true, 1, new int[]{1}, List.of(),
                    1, 0, warmup, measurement, Mode.AverageTime, values, TimeUnit.NANOSECONDS, 1,
                    "java", List.of(), "21.0.8", "test", "test", "1.37", TimeValue.seconds(120));
            var recorder = new BoundaryRecorder();
            rejects(() -> recorder.afterIteration(benchmark, warmup, null));
            rejects(() -> recorder.beforeIteration(benchmark, measurement));
            for (int i = 0; i < 10; i++) {
                var iteration = i < 5 ? warmup : measurement;
                recorder.beforeIteration(benchmark, iteration);
                rejects(() -> recorder.beforeIteration(benchmark, iteration));
                recorder.afterIteration(benchmark, iteration, null);
                if (i < 9 && Files.exists(output)) throw new AssertionError("Premature completion");
            }
            var record = new Properties();
            try (var input = Files.newInputStream(output)) { record.loadFromXML(input); }
            if (!"true".equals(record.getProperty("complete"))
                    || !"HOOK".equals(record.getProperty("profile"))
                    || !"synthetic-stage".equals(record.getProperty("stage"))
                    || !Long.toString(ProcessHandle.current().pid()).equals(record.getProperty("pid"))) {
                throw new AssertionError("Missing identity/completion");
            }
            long previous = Long.MIN_VALUE;
            for (int i = 0; i < 10; i++) {
                for (String phase : List.of("before", "after")) {
                    long now = Long.parseLong(record.getProperty("iteration." + i + "." + phase + ".nano"));
                    if (now < previous) throw new AssertionError("Non-monotonic boundary");
                    previous = now;
                }
            }
            rejects(() -> recorder.beforeIteration(benchmark, measurement));
            System.out.println("BoundaryRecorder callback contract passed; no benchmark executed");
        } finally {
            Files.deleteIfExists(output);
            Files.delete(directory);
        }
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Expected rejected callback sequence");
    }
}

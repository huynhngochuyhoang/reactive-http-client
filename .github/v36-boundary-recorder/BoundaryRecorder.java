package evidence.v36;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.runner.IterationType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/** Diagnostic iteration envelopes; never installed in the ordinary lane. */
public final class BoundaryRecorder implements InternalProfiler {
    private final Properties record = new Properties();
    private int iteration;
    private boolean pending;

    @Override
    public String getDescription() {
        return "V36 worker identity and iteration envelopes (not timed-loop boundaries)";
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmark, IterationParams params) {
        if (pending || iteration >= 10
                || params.getType() != (iteration < 5 ? IterationType.WARMUP : IterationType.MEASUREMENT)) {
            throw new IllegalStateException("Unexpected V36 iteration sequence");
        }
        if (iteration == 0) {
            record.setProperty("schema", "v36-1");
            record.setProperty("stage", System.getProperty("v36.stage"));
            record.setProperty("seal", System.getProperty("v36.seal"));
            record.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
            record.setProperty("classpath", System.getProperty("java.class.path"));
            record.setProperty("benchmark", benchmark.getBenchmark());
            record.setProperty("profile", benchmark.getParam("profile"));
            record.setProperty("scenario", benchmark.getParam("scenario"));
            record.setProperty("recorderOrigin", BoundaryRecorder.class.getProtectionDomain().getCodeSource().getLocation().toString());
            record.setProperty("jmhOrigin", BenchmarkParams.class.getProtectionDomain().getCodeSource().getLocation().toString());
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            record.setProperty("argumentCount", Integer.toString(args.size()));
            for (int i = 0; i < args.size(); i++) record.setProperty("argument." + i, args.get(i));
        }
        record.setProperty("iteration." + iteration + ".type", params.getType().name());
        pending = true;
        mark("before");
    }

    @Override
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmark, IterationParams params,
                                                       IterationResult result) {
        if (!pending || !params.getType().name().equals(record.getProperty("iteration." + iteration + ".type"))) {
            throw new IllegalStateException("Unpaired V36 iteration");
        }
        mark("after");
        pending = false;
        if (++iteration == 10) {
            record.setProperty("complete", "true");
            Path path = Path.of(System.getProperty("v36.boundaries"));
            try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                record.storeToXML(output, "V36 diagnostic envelopes, not exact measurement code identity");
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }
        return List.of();
    }

    private void mark(String phase) {
        String prefix = "iteration." + iteration + "." + phase;
        long nano = System.nanoTime();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        long nanoAfter = System.nanoTime();
        record.setProperty(prefix + ".nano", Long.toString(nano));
        record.setProperty(prefix + ".nanoAfter", Long.toString(nanoAfter));
        record.setProperty(prefix + ".uptimeMs", Long.toString(uptime));
    }
}

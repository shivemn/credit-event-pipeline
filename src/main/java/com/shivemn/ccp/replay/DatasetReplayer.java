package com.shivemn.ccp.replay;

import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.pipeline.EventPublisher;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Replays a loan dataset (CSV) into the pipeline at a chosen speed, as if banks were reporting live.
 * Reads the file as a stream and starts again from the top when it reaches the end.
 */
@Component
public class DatasetReplayer {

    private static final Logger log = LoggerFactory.getLogger(DatasetReplayer.class);
    private static final int TICKS_PER_SECOND = 10;
    private static final int MAX_RATE = 5_000;

    private final EventPublisher publisher;
    private final Path file;
    private final LoanEventMapper mapper;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "dataset-replay"));

    private final AtomicLong sent = new AtomicLong();
    private ScheduledFuture<?> task;
    private int rate;
    private int lap;
    private String error;
    private CsvRecordReader csv;
    private List<String> header;
    private final ArrayDeque<CreditEvent> queue = new ArrayDeque<>();

    public DatasetReplayer(EventPublisher publisher,
                           @Value("${pipeline.replay.file}") String file,
                           @Value("${pipeline.replay.borrower-pool}") int borrowerPool) {
        this.publisher = publisher;
        this.file = Path.of(file);
        this.mapper = new LoanEventMapper(borrowerPool);
    }

    public synchronized void start(int eventsPerSecond) {
        stop();
        if (!Files.isReadable(file)) {
            error = "Dataset file not found: " + file.toAbsolutePath();
            return;
        }
        error = null;
        rate = Math.max(1, Math.min(eventsPerSecond, MAX_RATE));
        int perTick = Math.max(1, rate / TICKS_PER_SECOND);
        long periodMs = Math.max(1, 1000L * perTick / rate);
        task = scheduler.scheduleAtFixedRate(() -> tick(perTick), 0, periodMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        closeFile();
    }

    public synchronized Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", task != null);
        status.put("rate", rate);
        status.put("sent", sent.get());
        status.put("lap", lap);
        status.put("file", file.getFileName().toString());
        status.put("fileFound", Files.isReadable(file));
        // The bundled file is made-up sample data; a real download should be named differently.
        status.put("sampleData", file.getFileName().toString().toLowerCase().contains("sample"));
        status.put("error", error);
        return status;
    }

    @PreDestroy
    void shutdown() {
        stop();
        scheduler.shutdownNow();
    }

    private synchronized void tick(int count) {
        try {
            for (int i = 0; i < count; i++) {
                if (queue.isEmpty() && !fillQueue()) {
                    return;
                }
                if (task == null) {
                    return;
                }
                publisher.publish(queue.poll());
                sent.incrementAndGet();
            }
        } catch (Exception e) {
            log.error("Dataset replay stopped", e);
            error = "Replay stopped: " + e.getMessage();
            if (task != null) {
                task.cancel(false);
                task = null;
            }
            closeFile();
        }
    }

    /** Reads rows until some events are queued. Returns false if the file has nothing usable. */
    private boolean fillQueue() throws IOException {
        int emptyLaps = 0;
        while (queue.isEmpty()) {
            if (csv == null) {
                openFile();
            }
            List<String> record = csv.next();
            if (record == null) {
                closeFile();
                lap++;
                if (++emptyLaps > 1) {
                    throw new IOException("no usable rows found; check the column names (id, loan_amnt, loan_status)");
                }
                continue;
            }
            if (record.size() != header.size()) {
                continue; // blank line or footer
            }
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                row.put(header.get(i), record.get(i));
            }
            queue.addAll(mapper.toEvents(row, lap));
        }
        return true;
    }

    private void openFile() throws IOException {
        BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        csv = new CsvRecordReader(reader);
        // Some downloads start with a note line before the column names, so look for the header row.
        List<String> record;
        while ((record = csv.next()) != null) {
            if (record.contains("id") && record.contains("loan_amnt")) {
                header = record.stream().map(String::trim).toList();
                return;
            }
        }
        closeFile();
        throw new IOException("no header row with 'id' and 'loan_amnt' columns");
    }

    private void closeFile() {
        if (csv != null) {
            try {
                csv.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
            csv = null;
        }
    }
}

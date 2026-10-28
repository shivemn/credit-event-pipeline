package com.shivemn.ccp.web;

import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.pipeline.EventPublisher;
import com.shivemn.ccp.pipeline.SyntheticEventGenerator;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventPublisher publisher;
    private final SyntheticEventGenerator generator;
    private final int maxGenerate;

    public EventController(EventPublisher publisher, SyntheticEventGenerator generator,
                           @Value("${pipeline.limits.max-generate:100000}") int maxGenerate) {
        this.publisher = publisher;
        this.generator = generator;
        this.maxGenerate = maxGenerate;
    }

    /** Submit a single event. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@RequestBody CreditEvent event) {
        if (event.borrowerId() == null || event.borrowerId().isBlank() || event.type() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "borrowerId and type are required"));
        }
        publisher.publish(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("published", 1));
    }

    /** Generate and publish random events, handy for load testing the pipeline. */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @RequestParam(defaultValue = "1000") int count,
            @RequestParam(defaultValue = "200") int borrowers) {
        if (count < 1 || count > maxGenerate || borrowers < 1) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "count must be 1.." + maxGenerate + " and borrowers must be >= 1"));
        }
        generator.generate(count, borrowers).forEach(publisher::publish);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("published", count));
    }
}

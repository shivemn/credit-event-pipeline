package com.shivemn.ccp.web;

import com.shivemn.ccp.replay.DatasetReplayer;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Start, stop and check the loan dataset replay. */
@RestController
@RequestMapping("/api/replay")
public class ReplayController {

    private final DatasetReplayer replayer;

    public ReplayController(DatasetReplayer replayer) {
        this.replayer = replayer;
    }

    @GetMapping
    public Map<String, Object> status() {
        return replayer.status();
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestParam(defaultValue = "200") int rate) {
        replayer.start(rate);
        return replayer.status();
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        replayer.stop();
        return replayer.status();
    }
}

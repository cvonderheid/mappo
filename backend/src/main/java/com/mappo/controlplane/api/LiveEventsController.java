package com.mappo.controlplane.api;

import com.mappo.controlplane.service.live.LiveUpdateService;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Hidden
public class LiveEventsController {

    private final LiveUpdateService liveUpdateService;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(value = "topics", required = false) String topics) {
        return liveUpdateService.subscribe(parseTopics(topics));
    }

    private Set<String> parseTopics(String topics) {
        if (topics == null || topics.isBlank()) {
            return Set.of();
        }
        Set<String> parsed = new LinkedHashSet<>();
        Arrays.stream(topics.split(","))
            .map(value -> value == null ? "" : value.trim().toLowerCase())
            .filter(value -> !value.isBlank())
            .forEach(parsed::add);
        return parsed;
    }
}

package com.mappo.controlplane.api;

import com.mappo.controlplane.service.live.LiveUpdateService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
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
    public SseEmitter stream() {
        return liveUpdateService.subscribe();
    }
}

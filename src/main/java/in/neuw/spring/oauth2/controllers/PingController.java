package in.neuw.spring.oauth2.controllers;

import in.neuw.spring.oauth2.models.Ping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

@Slf4j
@RestController
public class PingController {

    private final RestClient restClient;

    public PingController(RestClient restClient) {
        this.restClient = restClient;
    }

    // this is to trigger the flow, to call an endpoint that is protected by OAuth2
    @GetMapping("/api/upstream/ping")
    public Ping upstreamPing() {
        var now = LocalDateTime.now();
        log.info("Pinging upstream ping at {}", now);
        return restClient.get().uri("/api/downstream/ping").retrieve().body(Ping.class);
    }

    // this is like downstream resource that will be protected by OAuth2 token
    @GetMapping("/api/downstream/ping")
    public Ping ping(@RequestHeader("Authorization") String authorization) {
        var now = LocalDateTime.now();
        // logged only for POC, one should not log the Authorization otherwise!
        log.info("Ping sent at {} with Authorization = {}", now, authorization);
        return new Ping()
                .setMessage("PING - Hello World sent at "+now)
                .setTime(now);
    }

}

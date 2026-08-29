package com.vanter.ember.config;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated liveness probe for the external (Cloud Monitoring) uptime check. */
@RestController
@RequestMapping("/public")
public class PublicPingController {

    @GetMapping(value = "/ping", produces = MediaType.TEXT_PLAIN_VALUE)
    public String ping() {
        return "pong";
    }
}

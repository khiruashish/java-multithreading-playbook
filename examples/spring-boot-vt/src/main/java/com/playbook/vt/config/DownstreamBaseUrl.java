package com.playbook.vt.config;

import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/** Holds the downstream base URL; in self-hosted mode it is fixed up with the real port after startup. */
@Component
public class DownstreamBaseUrl implements ApplicationListener<WebServerInitializedEvent> {

    private final DownstreamProperties props;
    private volatile String url;

    public DownstreamBaseUrl(DownstreamProperties props) {
        this.props = props;
        this.url = props.baseUrl();
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (props.selfHosted()) {
            url = "http://localhost:" + event.getWebServer().getPort() + "/sim";
        }
    }

    public String get() {
        return url;
    }
}

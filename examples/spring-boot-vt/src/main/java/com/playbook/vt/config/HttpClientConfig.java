package com.playbook.vt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class HttpClientConfig {

    /**
     * JDK HttpClient whose internal async tasks also run on virtual threads. Blocking {@code send()} from a virtual
     * thread parks it (no carrier is held). Timeouts are mandatory: a VT blocked forever is still a leak.
     */
    @Bean(destroyMethod = "close")
    HttpClient jdkHttpClient(DownstreamProperties props) {
        return HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(props.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Bean
    RestClient downstreamRestClient(RestClient.Builder builder, HttpClient jdkHttpClient, DownstreamProperties props) {
        var factory = new JdkClientHttpRequestFactory(jdkHttpClient);
        factory.setReadTimeout(props.readTimeout());
        return builder.requestFactory(factory).build();
    }
}

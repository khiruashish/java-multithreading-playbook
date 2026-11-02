package com.playbook.vt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("playbook.simulation")
public record SimulationProperties(Duration catalogLatency, Duration pricingLatency, Duration inventoryLatency) {}

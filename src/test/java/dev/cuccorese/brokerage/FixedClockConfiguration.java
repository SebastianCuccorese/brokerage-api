package dev.cuccorese.brokerage;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Stops the application clock at {@link #NOW}, Buenos Aires local time. */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

    public static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    public static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 11, 30);

    @Bean
    @Primary
    Clock fixedClock() {
        Instant instant = NOW.atZone(BUENOS_AIRES).toInstant();
        return Clock.fixed(instant, BUENOS_AIRES);
    }
}

package dev.cuccorese.brokerage.shared;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Orders are timestamped in Buenos Aires local time, the market's time, which is how the
 * provided orders are stored ({@code TIMESTAMP} without a time zone).
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}

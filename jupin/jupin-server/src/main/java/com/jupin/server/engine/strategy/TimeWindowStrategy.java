package com.jupin.server.engine.strategy;

import com.jupin.server.engine.MatchContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class TimeWindowStrategy implements MatchStrategy {
    @Override
    public int score(MatchContext ctx) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = ctx.getPool().getStartTime();
        if (start == null || start.isBefore(now)) return 0;

        long hoursUntilStart = Duration.between(now, start).toHours();
        if (hoursUntilStart <= 2) return 90;
        if (hoursUntilStart <= 24) return 80;
        if (hoursUntilStart <= 72) return 60;
        if (hoursUntilStart <= 168) return 40;
        return 10;
    }

    @Override
    public int weight() { return 20; }
}

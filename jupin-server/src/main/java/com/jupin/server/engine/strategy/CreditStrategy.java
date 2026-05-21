package com.jupin.server.engine.strategy;

import com.jupin.server.engine.MatchContext;
import org.springframework.stereotype.Component;

@Component
public class CreditStrategy implements MatchStrategy {
    @Override
    public int score(MatchContext ctx) {
        int credit = ctx.getUser().getCreditScore();
        if (credit >= 90) return 100;
        if (credit >= 80) return 80;
        if (credit >= 70) return 60;
        if (credit >= 60) return 40;
        return 0;
    }

    @Override
    public int weight() { return 25; }
}

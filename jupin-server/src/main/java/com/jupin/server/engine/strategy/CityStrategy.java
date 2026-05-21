package com.jupin.server.engine.strategy;

import com.jupin.server.engine.MatchContext;
import org.springframework.stereotype.Component;

@Component
public class CityStrategy implements MatchStrategy {
    @Override
    public int score(MatchContext ctx) {
        String poolCity = ctx.getPool().getCity();
        String userCity = ctx.getUser().getCity();
        if (poolCity == null || userCity == null) return 0;
        if (poolCity.equals(userCity)) return 100;
        if (poolCity.contains(userCity.substring(0, 2)) || userCity.contains(poolCity.substring(0, 2))) return 40;
        return 0;
    }

    @Override
    public int weight() { return 30; }
}

package com.jupin.server.engine.strategy;

import com.jupin.server.engine.MatchContext;

public interface MatchStrategy {
    int score(MatchContext context);
    int weight();
}

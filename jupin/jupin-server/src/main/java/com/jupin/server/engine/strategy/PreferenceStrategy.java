package com.jupin.server.engine.strategy;

import com.jupin.server.engine.MatchContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PreferenceStrategy implements MatchStrategy {
    @Override
    public int score(MatchContext ctx) {
        String poolType = ctx.getPool().getScriptType();
        String userPref = ctx.getUser().getPreference();
        if (!StringUtils.hasText(poolType) || !StringUtils.hasText(userPref)) return 50;

        Set<String> prefs = Arrays.stream(userPref.split(","))
                .map(String::trim).collect(Collectors.toSet());
        if (prefs.contains(poolType)) return 100;
        if (prefs.contains("推理") && "硬核".equals(poolType)) return 70;
        return 20;
    }

    @Override
    public int weight() { return 25; }
}

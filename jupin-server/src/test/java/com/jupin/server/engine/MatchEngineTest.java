package com.jupin.server.engine;

import com.jupin.server.engine.strategy.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchEngineTest {

    @Test
    void testCityStrategy() {
        CityStrategy s = new CityStrategy();
        var pool = new com.jupin.pojo.entity.CarPool();
        pool.setCity("上海");
        var user = new com.jupin.pojo.entity.User();
        user.setCity("上海");
        var ctx = new MatchContext(pool, user);
        assertEquals(100, s.score(ctx));
    }

    @Test
    void testCreditStrategyFiltersLowScore() {
        CreditStrategy s = new CreditStrategy();
        var pool = new com.jupin.pojo.entity.CarPool();
        var user = new com.jupin.pojo.entity.User();
        user.setCreditScore(50);
        var ctx = new MatchContext(pool, user);
        assertEquals(0, s.score(ctx), "信用分<60应该返回0");
    }

    @Test
    void testTimeWindowStrategy() {
        TimeWindowStrategy s = new TimeWindowStrategy();
        var pool = new com.jupin.pojo.entity.CarPool();
        pool.setStartTime(java.time.LocalDateTime.now().plusHours(3));
        var user = new com.jupin.pojo.entity.User();
        var ctx = new MatchContext(pool, user);
        int score = s.score(ctx);
        assertTrue(score > 0, "3小时后开团应该能匹配");
    }

    @Test
    void testPreferenceStrategyMatch() {
        PreferenceStrategy s = new PreferenceStrategy();
        var pool = new com.jupin.pojo.entity.CarPool();
        pool.setScriptType("硬核");
        var user = new com.jupin.pojo.entity.User();
        user.setPreference("硬核,情感");
        var ctx = new MatchContext(pool, user);
        assertEquals(100, s.score(ctx));
    }
}

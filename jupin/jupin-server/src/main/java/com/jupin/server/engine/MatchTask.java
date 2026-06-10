package com.jupin.server.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "jupin.match", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MatchTask {

    private final MatchEngine matchEngine;

    @Scheduled(fixedRate = 60000)
    public void autoMatch() {
        log.debug("匹配引擎开始扫描...");
        Map<Long, List<MatchEngine.MatchResult>> results = matchEngine.matchAll();
        if (!results.isEmpty()) {
            log.info("本轮匹配完成，{} 个拼车有推荐结果", results.size());
        }
    }
}

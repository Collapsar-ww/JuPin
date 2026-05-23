package com.jupin.server.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.PoolMember;
import com.jupin.pojo.entity.User;
import com.jupin.server.engine.strategy.MatchStrategy;
import com.jupin.server.mapper.PoolMapper;
import com.jupin.server.mapper.PoolMemberMapper;
import com.jupin.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchEngine {

    private final List<MatchStrategy> strategies;
    private final PoolMapper poolMapper;
    private final PoolMemberMapper memberMapper;
    private final UserMapper userMapper;

    public List<MatchResult> matchForPool(CarPool pool, int topN) {
        Set<Long> excludeIds = memberMapper.selectList(new QueryWrapper<PoolMember>()
                .eq("pool_id", pool.getId()).in("status", 0, 1))
                .stream().map(PoolMember::getUserId).collect(Collectors.toSet());
        excludeIds.add(pool.getOwnerId());

        List<User> candidates = userMapper.selectList(new QueryWrapper<User>()
                .eq("city", pool.getCity())
                .ge("credit_score", 60)
                .notIn("id", excludeIds));

        if (candidates.isEmpty()) return Collections.emptyList();

        return candidates.parallelStream()
                .map(user -> {
                    MatchContext ctx = new MatchContext(pool, user);
                    int total = strategies.stream()
                            .mapToInt(s -> s.score(ctx) * s.weight() / 100)
                            .sum();
                    return new MatchResult(user.getId(), user.getNickname(), total);
                })
                .filter(r -> r.getScore() > 0)
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .limit(topN)
                .collect(Collectors.toList());
    }

    public Map<Long, List<MatchResult>> matchAll() {
        Map<Long, List<MatchResult>> results = new HashMap<>();
        List<CarPool> openPools = poolMapper.selectList(new QueryWrapper<CarPool>()
                .eq("status", 0)
                .apply("current_members < max_members"));
        for (CarPool pool : openPools) {
            List<MatchResult> matches = matchForPool(pool, 5);
            if (!matches.isEmpty()) {
                results.put(pool.getId(), matches);
                log.info("拼车 {} 匹配结果: {}", pool.getId(),
                        matches.stream().map(MatchResult::getNickname).collect(Collectors.joining(",")));
            }
        }
        return results;
    }

    public record MatchResult(Long userId, String nickname, int score) {
        public Long getUserId() { return userId; }
        public String getNickname() { return nickname; }
        public int getScore() { return score; }
    }
}

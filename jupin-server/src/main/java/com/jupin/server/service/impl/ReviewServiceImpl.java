package com.jupin.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.dto.ReviewCreateRequest;
import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.Review;
import com.jupin.server.mapper.PoolMapper;
import com.jupin.server.mapper.ReviewMapper;
import com.jupin.server.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final PoolMapper poolMapper;

    @Override
    @Transactional
    public void create(Long userId, ReviewCreateRequest request) {
        CarPool pool = poolMapper.selectById(request.getPoolId());
        if (pool == null) throw new BaseException("拼车不存在");
        if (pool.getStatus() != 3) throw new BaseException("拼车未完成，不能评价");

        Long count = reviewMapper.selectCount(new QueryWrapper<Review>()
                .eq("pool_id", request.getPoolId())
                .eq("from_user_id", userId)
                .eq("type", request.getType()));
        if (count > 0) throw new BaseException("你已经评价过");

        Review review = Review.builder()
                .poolId(request.getPoolId())
                .fromUserId(userId)
                .targetId(request.getTargetId())
                .type(request.getType())
                .score(request.getScore())
                .content(request.getContent())
                .tags(request.getTags())
                .build();
        reviewMapper.insert(review);
    }

    @Override
    public List<Review> getByPool(Long poolId) {
        return reviewMapper.selectList(new QueryWrapper<Review>().eq("pool_id", poolId));
    }

    @Override
    public List<Review> getMyDmReviews(Long userId) {
        return reviewMapper.selectList(new QueryWrapper<Review>()
                .eq("target_id", userId).eq("type", 1));
    }

    @Override
    public List<Review> getShopReviews(Long shopId) {
        return reviewMapper.selectList(new QueryWrapper<Review>()
                .eq("target_id", shopId).eq("type", 0));
    }

    @Override
    public Map<String, Object> getDmSummary(Long userId) {
        List<Review> reviews = reviewMapper.selectList(new QueryWrapper<Review>()
                .eq("target_id", userId).eq("type", 1));
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", reviews.size());
        summary.put("avgScore", reviews.stream().mapToInt(Review::getScore).average().orElse(0));
        summary.put("tagCloud", reviews.stream()
                .filter(r -> r.getTags() != null)
                .flatMap(r -> List.of(r.getTags().split(",")).stream())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting())));
        return summary;
    }

    @Override
    public boolean isDmFrozen(Long userId) {
        List<Review> reviews = reviewMapper.selectList(new QueryWrapper<Review>()
                .eq("target_id", userId).eq("type", 1));
        if (reviews.size() < 3) return false;
        double avg = reviews.stream().mapToInt(Review::getScore).average().orElse(0);
        return avg < 2.0;
    }
}

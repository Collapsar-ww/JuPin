package com.jupin.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jupin.common.constant.DbFieldConstant;
import com.jupin.common.constant.ErrorConstant;
import com.jupin.common.constant.MemberStatus;
import com.jupin.common.constant.PoolStatus;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.dto.ReviewCreateRequest;
import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.PoolMember;
import com.jupin.pojo.entity.Review;
import com.jupin.server.mapper.PoolMemberMapper;
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
    private final PoolMemberMapper memberMapper;

    @Override
    @Transactional
    public void create(Long userId, ReviewCreateRequest request) {
        CarPool pool = poolMapper.selectById(request.getPoolId());
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        if (pool.getStatus() != PoolStatus.FINISHED) throw new BaseException(ErrorConstant.POOL_NOT_FINISHED_CANNOT_REVIEW);
        if (request.getType() == null || (request.getType() != 0 && request.getType() != 1)) {
            throw new BaseException(ErrorConstant.REVIEW_TYPE_INVALID);
        }

        PoolMember member = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, request.getPoolId())
                .eq(DbFieldConstant.USER_ID, userId)
                .eq(DbFieldConstant.STATUS, MemberStatus.JOINED));
        if (member == null) throw new BaseException(ErrorConstant.REVIEWER_NOT_POOL_MEMBER);

        if (request.getType() == 0) {
            if (pool.getType() != 1 || pool.getShopId() == null || !pool.getShopId().equals(request.getTargetId())) {
                throw new BaseException(ErrorConstant.REVIEW_TARGET_INVALID);
            }
        } else if (pool.getDmId() == null || !pool.getDmId().equals(request.getTargetId())) {
            throw new BaseException(ErrorConstant.REVIEW_TARGET_INVALID);
        }

        Long count = reviewMapper.selectCount(new QueryWrapper<Review>()
                .eq(DbFieldConstant.POOL_ID, request.getPoolId())
                .eq(DbFieldConstant.FROM_USER_ID, userId)
                .eq(DbFieldConstant.TYPE, request.getType()));
        if (count > 0) throw new BaseException(ErrorConstant.ALREADY_REVIEWED);

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
        return reviewMapper.selectList(new QueryWrapper<Review>().eq(DbFieldConstant.POOL_ID, poolId));
    }

    @Override
    public List<Review> getMyDmReviews(Long userId) {
        return reviewMapper.selectList(new QueryWrapper<Review>()
                .eq(DbFieldConstant.TARGET_ID, userId).eq(DbFieldConstant.TYPE, 1));
    }

    @Override
    public List<Review> getShopReviews(Long shopId) {
        return reviewMapper.selectList(new QueryWrapper<Review>()
                .eq(DbFieldConstant.TARGET_ID, shopId).eq(DbFieldConstant.TYPE, 0));
    }

    @Override
    public Map<String, Object> getDmSummary(Long userId) {
        List<Review> reviews = reviewMapper.selectList(new QueryWrapper<Review>()
                .eq(DbFieldConstant.TARGET_ID, userId).eq(DbFieldConstant.TYPE, 1));
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
                .eq(DbFieldConstant.TARGET_ID, userId).eq(DbFieldConstant.TYPE, 1));
        if (reviews.size() < 3) return false;
        double avg = reviews.stream().mapToInt(Review::getScore).average().orElse(0);
        return avg < 2.0;
    }
}

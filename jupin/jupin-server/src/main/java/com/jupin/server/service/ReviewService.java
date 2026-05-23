package com.jupin.server.service;

import com.jupin.pojo.dto.ReviewCreateRequest;
import com.jupin.pojo.entity.Review;

import java.util.List;
import java.util.Map;

public interface ReviewService {
    void create(Long userId, ReviewCreateRequest request);
    List<Review> getByPool(Long poolId);
    List<Review> getMyDmReviews(Long userId);
    List<Review> getShopReviews(Long shopId);
    Map<String, Object> getDmSummary(Long userId);
    boolean isDmFrozen(Long userId);
}

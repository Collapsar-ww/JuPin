package com.jupin.server.controller.shop;

import com.jupin.common.result.Result;
import com.jupin.pojo.entity.Review;
import com.jupin.pojo.vo.ReviewVO;
import com.jupin.server.converter.VOConverter;
import com.jupin.server.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "店家端-评价")
@RestController
@RequestMapping("/api/shop/review")
@RequiredArgsConstructor
public class ShopReviewController {

    private final ReviewService reviewService;
    private final VOConverter converter;

    @Operation(summary = "我的店铺收到的评价  🔒")
    @GetMapping("/my")
    public Result<List<ReviewVO>> my(@RequestParam Long shopId) {
        List<Review> reviews = reviewService.getShopReviews(shopId);
        List<ReviewVO> vos = reviews.stream().map(converter::toReviewVO).collect(Collectors.toList());
        return Result.success(vos);
    }
}

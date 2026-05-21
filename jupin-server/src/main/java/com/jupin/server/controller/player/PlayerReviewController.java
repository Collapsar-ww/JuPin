package com.jupin.server.controller.player;

import com.jupin.common.context.BaseContext;
import com.jupin.common.result.Result;
import com.jupin.pojo.dto.ReviewCreateRequest;
import com.jupin.pojo.entity.Review;
import com.jupin.pojo.vo.ReviewVO;
import com.jupin.server.converter.VOConverter;
import com.jupin.server.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "玩家端-评价")
@RestController
@RequestMapping("/api/player/review")
@RequiredArgsConstructor
public class PlayerReviewController {

    private final ReviewService reviewService;
    private final VOConverter converter;

    @Operation(summary = "提交评价  🔒")
    @PostMapping("/create")
    public Result<Void> create(@Valid @RequestBody ReviewCreateRequest request) {
        reviewService.create(BaseContext.getCurrentId(), request);
        return Result.success();
    }

    @Operation(summary = "我作为DM收到的评价  🔒")
    @GetMapping("/my-dm")
    public Result<List<ReviewVO>> myDmReviews() {
        List<Review> reviews = reviewService.getMyDmReviews(BaseContext.getCurrentId());
        List<ReviewVO> vos = reviews.stream().map(converter::toReviewVO).collect(Collectors.toList());
        return Result.success(vos);
    }
}

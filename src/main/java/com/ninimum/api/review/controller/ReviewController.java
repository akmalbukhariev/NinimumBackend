package com.ninimum.api.review.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.ReviewDto;
import com.ninimum.api.param.AddReviewParam;
import com.ninimum.api.param.DeleteReviewParam;
import com.ninimum.api.param.ReviewListParam;
import com.ninimum.api.review.service.IReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Review", description = "Product review APIs.")
@RequestMapping(value={"/samokat/api/v1/review"})
public class ReviewController extends BaseController {

    private final IReviewService reviewService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Review"},
            summary = "1. Review list",
            description = "Returns product review list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getReviewList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getReviewList(@RequestBody ReviewListParam param) {
        VersionResponseResult result = null;

        try {
            List<ReviewDto> reviews = this.reviewService.getReviewList(param);
            result = this.setResult(Result.SUCCESS, reviews);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ReviewController => getReviewList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Review"},
            summary = "2. Add review",
            description = "Adds product review.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/addReview", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> addReview(@RequestBody AddReviewParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.reviewService.addReview(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ReviewController => addReview: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Review"},
            summary = "3. Delete review",
            description = "Deletes product review.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @DeleteMapping(value = "/deleteReview", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> deleteReview(@RequestBody DeleteReviewParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.reviewService.deleteReview(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ReviewController => deleteReview: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
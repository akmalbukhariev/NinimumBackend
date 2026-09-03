package com.ninimum.api.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.ReviewDto;
import com.ninimum.api.param.AddReviewParam;
import com.ninimum.api.param.DeleteReviewParam;
import com.ninimum.api.param.ReviewListParam;
import com.ninimum.api.param.ReviewEligibilityParam;
import com.ninimum.api.param.UpdateReviewParam;
import com.ninimum.api.response.ReviewResponse;
import com.ninimum.api.response.ReviewEligibilityResponse;
import com.ninimum.api.dto.UserDto;
import com.ninimum.api.user.service.IUserService;
import com.ninimum.api.review.service.IReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Review", description = "Product review APIs.")
@RequestMapping(value={"/ninimum/api/v1/review"})
public class ReviewController extends BaseController {

    private final IReviewService reviewService;
    private final IUserService userService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Review"},
            summary = "1. Review list",
            description = "Returns product review list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") }
    )
    @PostMapping(value = "/getReviewList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getReviewList(@RequestBody ReviewListParam param) {
        VersionResponseResult result = null;

        try {
            List<ReviewResponse> reviews = this.reviewService.getReviewList(param);
            result = this.setResult(Result.SUCCESS, reviews);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ReviewController => getReviewList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Review"},
            summary = "2. Review eligibility",
            description = "Checks whether the authenticated user has an eligible paid purchase for this product.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getReviewEligibility", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getReviewEligibility(@RequestBody ReviewEligibilityParam param) {
        VersionResponseResult result;

        try {
            Long userId = getAuthenticatedUserId();
            ReviewEligibilityResponse eligibility = reviewService.getReviewEligibility(userId, param.getProduct_id());
            result = this.setResult(Result.SUCCESS, eligibility);
        } catch (Exception ex) {
            result = this.setResult(Result.AUTHENTICATION_ERROR);
            log.error("ReviewController => getReviewEligibility: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Review"},
            summary = "3. Add review",
            description = "Adds product review.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/addReview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> addReview(
            @RequestParam("data") String data,
            @RequestParam(value = "images", required = false) List<MultipartFile> images
    ) {
        VersionResponseResult result = null;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            AddReviewParam param = objectMapper.readValue(data, AddReviewParam.class);
            param.setUser_id(getAuthenticatedUserId());

            int resultNum = this.reviewService.addReview(param, images);

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
            summary = "4. Update review",
            description = "Updates the authenticated user's existing product review.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/updateReview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> updateReview(
            @RequestParam("data") String data,
            @RequestParam(value = "images", required = false) List<MultipartFile> images
    ) {
        VersionResponseResult result;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            UpdateReviewParam param = objectMapper.readValue(data, UpdateReviewParam.class);
            param.setUser_id(getAuthenticatedUserId());

            int resultNum = this.reviewService.updateReview(param, images);
            result = resultNum != 0 ? this.setResult(Result.SUCCESS) : this.setResult(Result.SERVER_ERROR);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ReviewController => updateReview: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Review"},
            summary = "5. Delete review",
            description = "Deletes product review.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @DeleteMapping(value = "/deleteReview", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> deleteReview(@RequestBody DeleteReviewParam param) {
        VersionResponseResult result = null;

        try {
            param.setUserId(getAuthenticatedUserId());
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
    private Long getAuthenticatedUserId() throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated.");
        }

        // The JWT subject is the user's phone number, not users.id.
        // Resolve the authenticated phone number to the real database user ID
        // before checking orders/reviews.
        UserDto user = userService.getUserByPhone(authentication.getName());
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("Authenticated user was not found.");
        }

        return user.getId();
    }

}
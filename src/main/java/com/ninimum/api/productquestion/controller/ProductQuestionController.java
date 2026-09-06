package com.ninimum.api.productquestion.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.ProductQuestionDto;
import com.ninimum.api.dto.UserDto;
import com.ninimum.api.param.AddProductQuestionParam;
import com.ninimum.api.param.AnswerProductQuestionParam;
import com.ninimum.api.param.ProductQuestionListParam;
import com.ninimum.api.productquestion.service.IProductQuestionService;
import com.ninimum.api.user.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Product Question", description = "Product question and answer APIs.")
@RequestMapping(value = {"/ninimum/api/v1/product-question"})
public class ProductQuestionController extends BaseController {

    private final IProductQuestionService productQuestionService;
    private final IUserService userService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Product Question"},
            summary = "1. Product question list",
            description = "Returns public questions and answers for a product.",
            responses = { @ApiResponse(responseCode = "200", description = "success") }
    )
    @PostMapping(value = "/getQuestionList", headers = {"Content-type=application/json"})
    public ResponseEntity<Object> getQuestionList(@RequestBody ProductQuestionListParam param) {
        VersionResponseResult result;

        try {
            List<ProductQuestionDto> questions = productQuestionService.getQuestionList(param);
            result = setResult(Result.SUCCESS, questions);
        } catch (Exception ex) {
            result = setResult(Result.SERVER_ERROR);
            log.error("ProductQuestionController => getQuestionList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Product Question"},
            summary = "2. Ask product question",
            description = "Adds a product question for the authenticated user. Purchase is not required.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/addQuestion", headers = {"Content-type=application/json"})
    public ResponseEntity<Object> addQuestion(@RequestBody AddProductQuestionParam param) {
        VersionResponseResult result;

        try {
            param.setUser_id(getAuthenticatedUserId());
            int resultNum = productQuestionService.addQuestion(param);
            result = resultNum > 0 ? setResult(Result.SUCCESS) : setResult(Result.FAILED);
        } catch (IllegalStateException ex) {
            result = setResult(Result.AUTHENTICATION_ERROR);
            log.warn("ProductQuestionController => addQuestion authentication: {}", ex.getMessage());
        } catch (Exception ex) {
            result = setResult(Result.SERVER_ERROR);
            log.error("ProductQuestionController => addQuestion: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Product Question"},
            summary = "3. Answer product question",
            description = "Admin answers an existing product question.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping(value = "/answerQuestion", headers = {"Content-type=application/json"})
    public ResponseEntity<Object> answerQuestion(@RequestBody AnswerProductQuestionParam param) {
        VersionResponseResult result;

        try {
            int resultNum = productQuestionService.answerQuestion(param);
            result = resultNum > 0 ? setResult(Result.SUCCESS) : setResult(Result.NOT_FOUND);
        } catch (Exception ex) {
            result = setResult(Result.SERVER_ERROR);
            log.error("ProductQuestionController => answerQuestion: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    private Long getAuthenticatedUserId() throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated.");
        }

        UserDto user = userService.getUserByPhone(authentication.getName());
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("Authenticated user was not found.");
        }

        return user.getId();
    }
}

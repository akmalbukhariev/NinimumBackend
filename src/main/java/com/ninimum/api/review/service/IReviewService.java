package com.ninimum.api.review.service;

import com.ninimum.api.dto.ReviewDto;
import com.ninimum.api.param.AddReviewParam;
import com.ninimum.api.param.DeleteReviewParam;
import com.ninimum.api.param.ReviewListParam;
import com.ninimum.api.response.ReviewResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IReviewService {
    List<ReviewResponse> getReviewList(ReviewListParam param) throws Exception;

    int addReview(AddReviewParam param, List<MultipartFile> images) throws Exception;

    int deleteReview(DeleteReviewParam param) throws Exception;
}
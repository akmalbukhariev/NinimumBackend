package com.ninimum.api.review.service;

import com.ninimum.api.dto.ReviewDto;
import com.ninimum.api.param.AddReviewParam;
import com.ninimum.api.param.DeleteReviewParam;
import com.ninimum.api.param.ReviewListParam;

import java.util.List;

public interface IReviewService {
    List<ReviewDto> getReviewList(ReviewListParam param) throws Exception;

    int addReview(AddReviewParam param) throws Exception;

    int deleteReview(DeleteReviewParam param) throws Exception;
}
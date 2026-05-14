package com.ninimum.api.review.service;

import com.ninimum.api.dto.ReviewDto;
import com.ninimum.api.param.AddReviewParam;
import com.ninimum.api.param.DeleteReviewParam;
import com.ninimum.api.param.ReviewListParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReviewMapper {
    List<ReviewDto> getReviewList(ReviewListParam param) throws Exception;

    int addReview(AddReviewParam param) throws Exception;

    int deleteReview(DeleteReviewParam param) throws Exception;
}
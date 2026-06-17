package com.ninimum.api.review.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.ReviewDto;
import com.ninimum.api.param.AddReviewParam;
import com.ninimum.api.param.DeleteReviewParam;
import com.ninimum.api.param.ReviewListParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReviewMapper {
    List<CamelCaseMap> getReviewList(ReviewListParam param) throws Exception;
    List<CamelCaseMap> getReviewImages(Long reviewId) throws Exception;

    Long getLastInsertId();

    int addReview(AddReviewParam param) throws Exception;

    int insertReviewImages(AddReviewParam param);

    int deleteReview(DeleteReviewParam param) throws Exception;
}
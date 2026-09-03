package com.ninimum.api.review.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.param.AddReviewParam;
import com.ninimum.api.param.DeleteReviewParam;
import com.ninimum.api.param.ReviewListParam;
import com.ninimum.api.param.UpdateReviewParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReviewMapper {
    List<CamelCaseMap> getReviewList(ReviewListParam param) throws Exception;
    List<CamelCaseMap> getReviewImages(Long reviewId) throws Exception;
    CamelCaseMap getLatestReviewByUserProduct(AddReviewParam param) throws Exception;
    CamelCaseMap getReviewByIdForUser(UpdateReviewParam param) throws Exception;

    Long getLastInsertId();

    Long getEligibleOrderId(AddReviewParam param) throws Exception;
    int hasPurchasedProduct(AddReviewParam param) throws Exception;

    int addReview(AddReviewParam param) throws Exception;
    int updateReview(UpdateReviewParam param) throws Exception;

    int insertReviewImages(AddReviewParam param);
    int insertUpdatedReviewImages(UpdateReviewParam param);
    int deleteRemovedReviewImages(UpdateReviewParam param);

    int deleteReview(DeleteReviewParam param) throws Exception;
}

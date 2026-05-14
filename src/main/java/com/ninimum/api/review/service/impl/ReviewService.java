package com.ninimum.api.review.service;

import com.ninimum.api.dto.ReviewDto;
import com.ninimum.api.param.AddReviewParam;
import com.ninimum.api.param.DeleteReviewParam;
import com.ninimum.api.param.ReviewListParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService implements IReviewService {

    private final ReviewMapper reviewMapper;

    @Override
    public List<ReviewDto> getReviewList(ReviewListParam param) throws Exception {
        return this.reviewMapper.getReviewList(param);
    }

    @Override
    public int addReview(AddReviewParam param) throws Exception {
        return this.reviewMapper.addReview(param);
    }

    @Override
    public int deleteReview(DeleteReviewParam param) throws Exception {
        return this.reviewMapper.deleteReview(param);
    }
}
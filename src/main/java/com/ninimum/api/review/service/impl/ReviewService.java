package com.ninimum.api.review.service.impl;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Converter;
import com.ninimum.api.dto.ReviewDto;
import com.ninimum.api.dto.ReviewImageDto;
import com.ninimum.api.file.service.impl.FileService;
import com.ninimum.api.param.AddReviewParam;
import com.ninimum.api.param.DeleteReviewParam;
import com.ninimum.api.param.ReviewImageParam;
import com.ninimum.api.param.ReviewListParam;
import com.ninimum.api.response.ReviewResponse;
import com.ninimum.api.review.service.IReviewService;
import com.ninimum.api.review.service.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService implements IReviewService {

    @Value("${file.access.url}")
    private String fileAccessUrl;

    private final FileService fileService;
    private final ReviewMapper reviewMapper;

    @Override
    public List<ReviewResponse> getReviewList(ReviewListParam param) throws Exception {

        List<CamelCaseMap> camReviews = this.reviewMapper.getReviewList(param);
        List<ReviewResponse> reviews = Converter.mapToDtoList(camReviews, ReviewResponse.class);

        for (ReviewResponse review : reviews) {

            List<CamelCaseMap> mapList = this.reviewMapper.getReviewImages(review.getId());
            List<ReviewImageDto> images =  Converter.mapToDtoList(mapList, ReviewImageDto.class);

            for (ReviewImageDto image : images) {
                if (image.getImage_url() != null && !image.getImage_url().isEmpty()) {
                    image.setImage_url(fileAccessUrl + "/" + image.getImage_url());
                }
            }

            review.setImages(images);
        }

        return reviews;
    }

    @Override
    @Transactional
    public int addReview(AddReviewParam param, List<MultipartFile> images) throws Exception {

        int reviewResult = reviewMapper.addReview(param);

        if (reviewResult == 0) {
            return 0;
        }

        Long reviewId = reviewMapper.getLastInsertId();
        param.setId(reviewId);

        if (reviewId == null) {
            return 0;
        }

        if (images != null && !images.isEmpty()) {

            List<ReviewImageParam> imageParams = new ArrayList<>();

            for (MultipartFile image : images) {
                String savedFileName = fileService.saveReviewImage(image);

                ReviewImageParam imageParam = new ReviewImageParam();
                imageParam.setReview_id(reviewId);
                imageParam.setImage_url(savedFileName);

                imageParams.add(imageParam);
            }

            param.setImages(imageParams);
            reviewMapper.insertReviewImages(param);
        }

        return reviewResult;
    }

    @Override
    public int deleteReview(DeleteReviewParam param) throws Exception {
        return this.reviewMapper.deleteReview(param);
    }
}
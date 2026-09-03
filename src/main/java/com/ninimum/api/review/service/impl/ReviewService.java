package com.ninimum.api.review.service.impl;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Converter;
import com.ninimum.api.dto.ReviewImageDto;
import com.ninimum.api.file.service.impl.FileService;
import com.ninimum.api.param.AddReviewParam;
import com.ninimum.api.param.DeleteReviewParam;
import com.ninimum.api.param.ReviewImageParam;
import com.ninimum.api.param.ReviewListParam;
import com.ninimum.api.param.UpdateReviewParam;
import com.ninimum.api.response.ReviewEligibilityResponse;
import com.ninimum.api.response.ReviewResponse;
import com.ninimum.api.review.service.IReviewService;
import com.ninimum.api.review.service.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            attachImages(review);
        }

        return reviews;
    }

    @Override
    public ReviewEligibilityResponse getReviewEligibility(Long userId, Long productId) throws Exception {
        AddReviewParam checkParam = new AddReviewParam();
        checkParam.setUser_id(userId);
        checkParam.setProduct_id(productId);

        Long eligibleOrderId = reviewMapper.getEligibleOrderId(checkParam);
        boolean hasPurchased = reviewMapper.hasPurchasedProduct(checkParam) > 0;

        ReviewResponse existingReview = null;
        CamelCaseMap existingReviewMap = reviewMapper.getLatestReviewByUserProduct(checkParam);
        if (existingReviewMap != null) {
            existingReview = existingReviewMap.toObject(ReviewResponse.class);
            attachImages(existingReview);
        }

        ReviewEligibilityResponse response = new ReviewEligibilityResponse();
        response.setCan_review(eligibleOrderId != null);
        response.setHas_purchased(hasPurchased);
        response.setAlready_reviewed(existingReview != null);
        response.setOrder_id(eligibleOrderId);
        response.setExisting_review(existingReview);
        return response;
    }

    @Override
    @Transactional
    public int addReview(AddReviewParam param, List<MultipartFile> images) throws Exception {
        if (param.getUser_id() == null || param.getProduct_id() == null || param.getOrder_id() == null ||
                param.getRating() == null || param.getRating() < 1 || param.getRating() > 5) {
            return 0;
        }

        if (images != null && images.size() > 3) {
            return 0;
        }

        Long eligibleOrderId = reviewMapper.getEligibleOrderId(param);
        if (eligibleOrderId == null || !param.getOrder_id().equals(eligibleOrderId)) {
            return 0;
        }

        normalizeComment(param);

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
            List<ReviewImageParam> imageParams = saveNewImages(reviewId, images);
            param.setImages(imageParams);
            reviewMapper.insertReviewImages(param);
        }

        return reviewResult;
    }

    @Override
    @Transactional
    public int updateReview(UpdateReviewParam param, List<MultipartFile> images) throws Exception {
        if (param.getId() == null || param.getUser_id() == null ||
                param.getRating() == null || param.getRating() < 1 || param.getRating() > 5) {
            return 0;
        }

        CamelCaseMap ownedReview = reviewMapper.getReviewByIdForUser(param);
        if (ownedReview == null) {
            return 0;
        }

        List<CamelCaseMap> currentImageMaps = reviewMapper.getReviewImages(param.getId());
        List<ReviewImageDto> currentImages = Converter.mapToDtoList(currentImageMaps, ReviewImageDto.class);

        Set<Long> currentImageIds = new HashSet<>();
        for (ReviewImageDto image : currentImages) {
            if (image.getId() != null) {
                currentImageIds.add(image.getId());
            }
        }

        List<Long> validKeepIds = new ArrayList<>();
        if (param.getKeep_image_ids() == null) {
            validKeepIds.addAll(currentImageIds);
        } else {
            for (Long imageId : param.getKeep_image_ids()) {
                if (imageId != null && currentImageIds.contains(imageId) && !validKeepIds.contains(imageId)) {
                    validKeepIds.add(imageId);
                }
            }
        }
        param.setKeep_image_ids(validKeepIds);

        int newImageCount = images == null ? 0 : images.size();
        if (validKeepIds.size() + newImageCount > 3) {
            return 0;
        }

        normalizeComment(param);

        int updateResult = reviewMapper.updateReview(param);
        if (updateResult == 0) {
            return 0;
        }

        List<ReviewImageDto> removedImages = new ArrayList<>();
        for (ReviewImageDto image : currentImages) {
            if (image.getId() != null && !validKeepIds.contains(image.getId())) {
                removedImages.add(image);
            }
        }

        reviewMapper.deleteRemovedReviewImages(param);

        for (ReviewImageDto image : removedImages) {
            fileService.deleteReviewImage(image.getImage_url());
        }

        if (images != null && !images.isEmpty()) {
            List<ReviewImageParam> imageParams = saveNewImages(param.getId(), images);
            param.setImages(imageParams);
            reviewMapper.insertUpdatedReviewImages(param);
        }

        return updateResult;
    }

    @Override
    public int deleteReview(DeleteReviewParam param) throws Exception {
        return this.reviewMapper.deleteReview(param);
    }

    private void attachImages(ReviewResponse review) throws Exception {
        List<CamelCaseMap> mapList = this.reviewMapper.getReviewImages(review.getId());
        List<ReviewImageDto> images = Converter.mapToDtoList(mapList, ReviewImageDto.class);

        for (ReviewImageDto image : images) {
            if (image.getImage_url() != null && !image.getImage_url().isEmpty()) {
                image.setImage_url(fileAccessUrl + "/" + image.getImage_url());
            }
        }

        review.setImages(images);
    }

    private List<ReviewImageParam> saveNewImages(Long reviewId, List<MultipartFile> images) throws Exception {
        List<ReviewImageParam> imageParams = new ArrayList<>();

        for (MultipartFile image : images) {
            String savedFileName = fileService.saveReviewImage(image);

            ReviewImageParam imageParam = new ReviewImageParam();
            imageParam.setReview_id(reviewId);
            imageParam.setImage_url(savedFileName);
            imageParams.add(imageParam);
        }

        return imageParams;
    }

    private void normalizeComment(AddReviewParam param) {
        if (param.getComment() == null) {
            param.setComment("");
            return;
        }

        String comment = param.getComment().trim();
        if (comment.length() > 2000) {
            comment = comment.substring(0, 2000);
        }
        param.setComment(comment);
    }

    private void normalizeComment(UpdateReviewParam param) {
        if (param.getComment() == null) {
            param.setComment("");
            return;
        }

        String comment = param.getComment().trim();
        if (comment.length() > 2000) {
            comment = comment.substring(0, 2000);
        }
        param.setComment(comment);
    }
}

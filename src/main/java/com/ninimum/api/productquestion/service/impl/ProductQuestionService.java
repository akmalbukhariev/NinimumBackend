package com.ninimum.api.productquestion.service.impl;

import com.ninimum.api.dto.ProductQuestionDto;
import com.ninimum.api.param.AddProductQuestionParam;
import com.ninimum.api.param.AnswerProductQuestionParam;
import com.ninimum.api.param.ProductQuestionListParam;
import com.ninimum.api.productquestion.service.IProductQuestionService;
import com.ninimum.api.productquestion.service.ProductQuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductQuestionService implements IProductQuestionService {

    private static final int MAX_QUESTION_LENGTH = 1000;
    private static final int MAX_ANSWER_LENGTH = 4000;

    private final ProductQuestionMapper productQuestionMapper;

    @Override
    public List<ProductQuestionDto> getQuestionList(ProductQuestionListParam param) throws Exception {
        if (param == null || param.getProduct_id() == null || param.getProduct_id() <= 0) {
            return Collections.emptyList();
        }

        if (param.getPageSize() <= 0 || param.getPageSize() > 100) {
            param.setPageSize(50);
        }
        if (param.getOffset() < 0) {
            param.setOffset(0);
        }

        return productQuestionMapper.getQuestionList(param);
    }

    @Override
    public int addQuestion(AddProductQuestionParam param) throws Exception {
        if (param == null || param.getUser_id() == null || param.getUser_id() <= 0 ||
                param.getProduct_id() == null || param.getProduct_id() <= 0) {
            return 0;
        }

        String question = normalize(param.getQuestion(), MAX_QUESTION_LENGTH);
        if (question.length() < 3) {
            return 0;
        }

        param.setQuestion(question);
        return productQuestionMapper.addQuestion(param);
    }

    @Override
    public int answerQuestion(AnswerProductQuestionParam param) throws Exception {
        if (param == null || param.getId() == null || param.getId() <= 0) {
            return 0;
        }

        String answer = normalize(param.getAnswer(), MAX_ANSWER_LENGTH);
        if (answer.length() < 2) {
            return 0;
        }

        param.setAnswer(answer);
        return productQuestionMapper.answerQuestion(param);
    }

    private String normalize(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
        }
        return normalized;
    }
}

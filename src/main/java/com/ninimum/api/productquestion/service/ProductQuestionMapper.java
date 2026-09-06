package com.ninimum.api.productquestion.service;

import com.ninimum.api.dto.ProductQuestionDto;
import com.ninimum.api.param.AddProductQuestionParam;
import com.ninimum.api.param.AnswerProductQuestionParam;
import com.ninimum.api.param.ProductQuestionListParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductQuestionMapper {
    List<ProductQuestionDto> getQuestionList(ProductQuestionListParam param) throws Exception;
    int addQuestion(AddProductQuestionParam param) throws Exception;
    int answerQuestion(AnswerProductQuestionParam param) throws Exception;
}

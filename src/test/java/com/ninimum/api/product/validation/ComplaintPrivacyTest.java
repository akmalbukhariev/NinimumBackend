package com.ninimum.api.product.validation;
import com.ninimum.api.productquestion.controller.ProductQuestionController;
import com.ninimum.api.productquestion.service.IProductQuestionService;
import com.ninimum.api.productquestion.service.ProductQuestionMapper;
import com.ninimum.api.productquestion.service.impl.ProductQuestionService;
import com.ninimum.api.user.service.IUserService;
import com.ninimum.api.param.*;
import com.ninimum.api.dto.UserDto;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ComplaintPrivacyTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void ignoresForgedViewerIdAndUsesAuthenticatedIdentity() throws Exception {
  var service=mock(IProductQuestionService.class);var users=mock(IUserService.class);var controller=new ProductQuestionController(service,users);
  var request=new ProductQuestionListParam();request.setProduct_id(1L);request.setViewer_id(999L);
  controller.getQuestionList(request);assertNull(request.getViewer_id());
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("phone",null,List.of()));
  var user=new UserDto();user.setId(12L);when(users.getUserByPhone("phone")).thenReturn(user);
  request.setViewer_id(999L);controller.getQuestionList(request);assertEquals(12L,request.getViewer_id());
 }
 @Test void validatesKindAndDefaultsLegacyRequestsToQuestions() throws Exception {
  var mapper=mock(ProductQuestionMapper.class);var service=new ProductQuestionService(mapper);var p=new AddProductQuestionParam();p.setUser_id(1L);p.setProduct_id(2L);p.setQuestion("  Question  ");
  service.addQuestion(p);assertEquals("QUESTION",p.getKind());assertEquals("Question",p.getQuestion());
  p.setKind(" complaint ");service.addQuestion(p);assertEquals("COMPLAINT",p.getKind());
  p.setKind("admin");assertThrows(IllegalArgumentException.class,()->service.addQuestion(p));
 }
 @Test void complaintDeadlineIsIndependentAndValidated() {
  var settings=Map.<String,Object>of("question_minutes",1440,"complaint_minutes",60);
  assertEquals(60,com.ninimum.api.ordersafety.QuestionResponsePolicy.minutes(settings,"COMPLAINT"));
  assertEquals(1440,com.ninimum.api.ordersafety.QuestionResponsePolicy.minutes(settings,"QUESTION"));
  assertThrows(IllegalArgumentException.class,()->com.ninimum.api.ordersafety.QuestionResponsePolicy.minutes(Map.of("complaint_minutes",0),"COMPLAINT"));
 }
}

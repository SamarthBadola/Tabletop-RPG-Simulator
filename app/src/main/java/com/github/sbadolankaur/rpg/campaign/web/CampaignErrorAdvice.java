package com.github.sbadolankaur.rpg.campaign.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = CampaignController.class)
class CampaignErrorAdvice {
  @ExceptionHandler(exception = CampaignException.class, produces = "text/html")
  ModelAndView browserError(CampaignException failure) {
    ProblemDetail problem = asProblem(failure);
    var view = new ModelAndView("demo-campaign-error");
    view.setStatus(HttpStatus.valueOf(problem.getStatus()));
    view.addObject("code", failure.code().name());
    view.addObject("detail", problem.getDetail());
    return view;
  }

  @ExceptionHandler(exception = CampaignException.class, produces = "application/problem+json")
  @ResponseBody
  ProblemDetail campaignError(CampaignException failure) {
    return asProblem(failure);
  }

  private static ProblemDetail asProblem(CampaignException failure) {
    HttpStatus status =
        switch (failure.code()) {
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case FORBIDDEN -> HttpStatus.FORBIDDEN;
          case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
          case IDEMPOTENCY_KEY_CONFLICT -> HttpStatus.CONFLICT;
        };
    String detail =
        switch (failure.code()) {
          case NOT_FOUND -> "This page could not be found.";
          case FORBIDDEN -> "You do not have permission to perform this action.";
          case VALIDATION_FAILED -> "Check the form and send it again.";
          case IDEMPOTENCY_KEY_CONFLICT -> "Open a new form before making another change.";
        };
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("urn:trpg:error:" + failure.code().name()));
    problem.setProperty("code", failure.code().name());
    return problem;
  }
}

package com.github.sbadolankaur.rpg.campaign.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(basePackageClasses = CampaignController.class)
class CampaignErrorAdvice {
  @ExceptionHandler(exception = CampaignException.class, produces = "text/html")
  ModelAndView browserError(CampaignException failure, HttpServletResponse response) {
    retryHeader(failure, response);
    ProblemDetail problem = asProblem(failure);
    var view = new ModelAndView("demo-campaign-error");
    view.setStatus(HttpStatus.valueOf(problem.getStatus()));
    view.addObject("code", failure.code().name());
    view.addObject("detail", problem.getDetail());
    return view;
  }

  @ExceptionHandler(exception = CampaignException.class, produces = "application/problem+json")
  @ResponseBody
  ProblemDetail campaignError(CampaignException failure, HttpServletResponse response) {
    retryHeader(failure, response);
    return asProblem(failure);
  }

  private static void retryHeader(CampaignException failure, HttpServletResponse response) {
    if (failure.retryAfter() > 0) {
      response.setHeader("Retry-After", Long.toString(failure.retryAfter()));
    }
  }

  private static ProblemDetail asProblem(CampaignException failure) {
    HttpStatus status =
        switch (failure.code()) {
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case FORBIDDEN, INVITE_INVALID, CSRF_INVALID -> HttpStatus.FORBIDDEN;
          case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
          case IDEMPOTENCY_KEY_CONFLICT, CAMPAIGN_VERSION_CONFLICT, SESSION_NOT_ACTIVE ->
              HttpStatus.CONFLICT;
          case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
          case SETTING_INVALID -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    String detail =
        switch (failure.code()) {
          case NOT_FOUND -> "This page could not be found.";
          case FORBIDDEN -> "You do not have permission to perform this action.";
          case CSRF_INVALID -> "Reload the page before submitting the form.";
          case VALIDATION_FAILED -> "Check the form and send it again.";
          case IDEMPOTENCY_KEY_CONFLICT -> "Open a new form before making another change.";
          case INVITE_INVALID -> "That code could not be used. Ask the DM for a new one.";
          case RATE_LIMITED -> "Too many attempts. Wait before trying again.";
          case CAMPAIGN_VERSION_CONFLICT -> "This changed while you were looking at it.";
          case SESSION_NOT_ACTIVE -> "Start or resume the session before taking that action.";
          case SETTING_INVALID -> "Choose an allowed value for the setting.";
        };
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("urn:trpg:error:" + failure.code().name()));
    problem.setProperty("code", failure.code().name());
    return problem;
  }
}

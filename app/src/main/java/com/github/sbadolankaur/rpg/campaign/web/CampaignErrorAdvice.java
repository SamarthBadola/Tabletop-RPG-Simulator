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

@ControllerAdvice(
    basePackages = {
      "com.github.sbadolankaur.rpg.campaign.web",
      "com.github.sbadolankaur.rpg.character.web",
      "com.github.sbadolankaur.rpg.encounter.web"
    })
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
          case IDEMPOTENCY_KEY_CONFLICT,
              CAMPAIGN_VERSION_CONFLICT,
              SESSION_NOT_ACTIVE,
              PACK_REVIEW_STALE,
              ENCOUNTER_ACTIVE,
              ENCOUNTER_STARTED,
              ENCOUNTER_VERSION_CONFLICT,
              SETTING_DECISION_REQUIRED ->
              HttpStatus.CONFLICT;
          case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
          case SETTING_INVALID,
              PACK_NOT_ENABLED,
              CONTENT_INVALID,
              NOT_YOUR_TURN,
              NO_ACTIONS_LEFT,
              TARGET_OUT_OF_RANGE,
              TOTAL_COVER,
              INVALID_TARGET ->
              HttpStatus.UNPROCESSABLE_CONTENT;
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
          case PACK_NOT_ENABLED -> "Ask the DM to enable the content pack for this campaign.";
          case PACK_REVIEW_STALE -> "Review the current pack changes again.";
          case CONTENT_INVALID -> "Resolve the issues shown in the content pack review.";
          case ENCOUNTER_VERSION_CONFLICT -> "This changed while you were looking at it.";
          case ENCOUNTER_ACTIVE -> "End the active encounter before doing this.";
          case ENCOUNTER_STARTED -> "This encounter has already started.";
          case NOT_YOUR_TURN -> "Choose the creature whose turn it is.";
          case NO_ACTIONS_LEFT -> "This creature has no action left for that.";
          case TARGET_OUT_OF_RANGE -> "This target is out of range.";
          case TOTAL_COVER -> "This target is behind total cover.";
          case INVALID_TARGET -> "Choose an eligible target.";
          case SETTING_DECISION_REQUIRED -> "Set the campaign rules for this action first.";
        };
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("urn:trpg:error:" + failure.code().name()));
    problem.setProperty("code", failure.code().name());
    if (!failure.fields().isEmpty()) problem.setProperty("errors", failure.fields());
    if (!failure.details().isEmpty()) problem.setProperty("details", failure.details());
    return problem;
  }
}

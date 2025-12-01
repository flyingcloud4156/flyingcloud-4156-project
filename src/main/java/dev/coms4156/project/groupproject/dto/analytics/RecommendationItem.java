package dev.coms4156.project.groupproject.dto.analytics;

import lombok.Data;

/** Recommendation item for analytics overview. */
@Data
public class RecommendationItem {
  private String code;
  private String message;
  private String severity;
}

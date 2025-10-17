package dev.coms4156.project.groupproject.dto;

import lombok.Data;

/** Token pair response (opaque access + refresh). */
@Data
public class TokenPair {
  private String accessToken;
  private String refreshToken;
}

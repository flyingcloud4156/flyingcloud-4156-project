package dev.coms4156.project.groupproject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for user lookup operations. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLookupResponse {
  private Long userId;
  private String name;
}

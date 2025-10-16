package dev.coms4156.project.groupproject.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Update profile request. */
@Data
public class UpdateProfileRequest {
    @Size(min = 1, max = 80)
    private String name;

    @Size(min = 1, max = 64)
    private String timezone;
}

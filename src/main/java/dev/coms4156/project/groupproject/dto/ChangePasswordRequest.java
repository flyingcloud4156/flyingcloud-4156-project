package dev.coms4156.project.groupproject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Change password request. */
@Data
public class ChangePasswordRequest {
    @NotBlank @Size(min = 6, max = 64)
    private String oldPassword;
    @NotBlank @Size(min = 6, max = 64)
    private String newPassword;
}

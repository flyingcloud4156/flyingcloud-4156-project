package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Registration request (email + password). */
@Data
public class RegisterRequest {
    @Email @NotBlank @Schema(example = "user@gmail.com")
    private String email;

    @NotBlank @Size(min = 1, max = 80)
    @Schema(example = "testU")
    private String name;

    @NotBlank @Size(min = 6, max = 64)
    @Schema(example = "S3cure!1")
    private String password;
}

package dev.coms4156.project.groupproject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Minimal user view for outbound responses. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserView {
    private Long id;
    private String name;
}

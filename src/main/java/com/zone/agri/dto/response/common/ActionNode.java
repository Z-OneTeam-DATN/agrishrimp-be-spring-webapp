package com.zone.agri.dto.response.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing an action-level permission node in the permission tree
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionNode {
    private Long id;
    private String code;
    private String name;
}

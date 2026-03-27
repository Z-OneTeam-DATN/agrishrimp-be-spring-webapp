package com.zone.agri.dto.response.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing a module-level permission node in the permission tree
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleNode {
    private Long id;
    private String code;
    private String name;

    @Builder.Default
    private List<ActionNode> actions = new ArrayList<>();
}

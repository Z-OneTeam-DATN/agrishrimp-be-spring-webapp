package com.zone.agri.dto.response.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing a permission group node in the permission tree
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupNode {
    private String groupName;
    private String groupLabel;

    @Builder.Default
    private List<ModuleNode> modules = new ArrayList<>();
}

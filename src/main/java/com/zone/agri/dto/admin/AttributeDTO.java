package com.zone.agri.dto.admin;

import com.zone.agri.entity.enums.AttributeStatus;
import lombok.Data;
import java.util.List;

@Data
public class AttributeDTO {
    private Long id;
    private String name;
    private String code;
    private String type;
    private String description;
    private AttributeStatus status;

    /** Danh sách giá trị khả dụng (đọc/ghi từ cột value_list trong DB) */
    private List<String> values;
}

package com.zone.agri.dto.request.returns;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class ReturnRequestReceiveRequest {
    String internalNote;
    List<@Valid ReturnRequestReceiveItemRequest> items;
}

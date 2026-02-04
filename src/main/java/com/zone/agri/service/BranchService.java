package com.zone.agri.service;

import com.zone.agri.dto.branch.BranchRequest;  // Sửa từ BranchDto sang cái này
import com.zone.agri.dto.branch.BranchResponse; // Sửa từ BranchDto sang cái này
import com.zone.agri.entity.Branch;
import com.zone.agri.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BranchService {
    private final BranchRepository branchRepository;

    public BranchResponse createBranch(BranchRequest request) { // Sửa tham số truyền vào
        // Logic xử lý...
        Branch branch = Branch.builder()
                .branchCode(request.getBranchCode())
                .name(request.getName())
                // ... map các trường khác
                .build();
        return BranchResponse.from(branchRepository.save(branch));
    }
}
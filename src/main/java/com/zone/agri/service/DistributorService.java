package com.zone.agri.service;

import com.zone.agri.dto.distributor.DistributorDto;
import java.util.List;

public interface DistributorService {

  List<DistributorDto> findAll();

  DistributorDto findById(Long id);

  DistributorDto create(DistributorDto distributorDto);

  DistributorDto update(Long id, DistributorDto distributorDto);

  void delete(Long id);
}

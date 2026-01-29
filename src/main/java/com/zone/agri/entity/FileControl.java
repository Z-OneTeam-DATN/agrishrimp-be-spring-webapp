package com.zone.agri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "file_control")
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FileControl extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  Long fileControlId;
  @Column(nullable = false)
  Long detailNo;
  String objectId;
  String fileId;
  String fileName;
  String deleteFlag;
}


package com.entloom.crud.engine.jdbc.test.entity;

import com.entloom.crud.annotations.EntCrudEntity;
import lombok.Getter;
import lombok.Setter;

@EntCrudEntity(table = "t_student", idField = "id", ownerService = "test-service")
@Getter
@Setter
public class StudentTestEntity {
    private Long id;
    private String studentName;
}

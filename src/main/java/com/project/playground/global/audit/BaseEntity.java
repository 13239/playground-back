package com.project.playground.global.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@MappedSuperclass
@EntityListeners(AuditorEntityListener.class)
@Getter
public abstract class BaseEntity extends TimeEntity {

    @NotNull
    @Column(updatable = false, nullable = false)
    @CreatedById
    private Long createdById;

    @LastModifiedById
    private Long updatedById;
}

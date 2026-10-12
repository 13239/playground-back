package com.project.playground.global.audit;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Objects;

@Component
public class AuditorEntityListener {

    @PrePersist
    public void setCreatedId(Object entity) throws IllegalAccessException {
        Class<?> clazz = entity.getClass();

        while (Objects.nonNull(clazz)) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(CreatedById.class)) {
                    field.setAccessible(true);
//                    field.set(entity, SecurityUtils.getId());
                }
            }

            clazz = clazz.getSuperclass();
        }
    }

    @PreUpdate
    public void setUpdatedId(Object entity) throws IllegalAccessException {
        Class<?> clazz = entity.getClass();

        while (Objects.nonNull(clazz)) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(LastModifiedById.class)) {
                    field.setAccessible(true);
//                    field.set(entity, SecurityUtils.getId());
                }
            }

            clazz = clazz.getSuperclass();
        }
    }
}

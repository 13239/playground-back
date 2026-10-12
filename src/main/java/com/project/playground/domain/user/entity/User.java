package com.project.playground.domain.user.entity;

import com.project.playground.global.audit.TimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tbl_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends TimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Builder
    public User(String name, String username, String password) {
        this.name = name;
        this.username = username;
        this.password = password;
    }
}

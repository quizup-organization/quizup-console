package io.github.quizup.console.infrastructure.out.persistence.entity;

import io.github.quizup.console.domain.model.AuditAction;
import io.github.quizup.console.domain.model.AuditResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "console_audit")
@Getter
@Setter
@NoArgsConstructor
public class ConsoleAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor")
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    @Column(name = "application")
    private String application;

    @Column(name = "instance_id")
    private String instanceId;

    @Column(name = "target")
    private String target;

    @Column(name = "parameters")
    private String parameters;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private AuditResult result;

    @Column(name = "error")
    private String error;
}

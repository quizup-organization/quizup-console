package io.github.quizup.console.infrastructure.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "console_dlq_stat")
@Getter
@Setter
@NoArgsConstructor
public class ConsoleDlqStatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "application", nullable = false)
    private String application;

    @Column(name = "instance_id", nullable = false)
    private String instanceId;

    @Column(name = "processor", nullable = false)
    private String processor;

    @Column(name = "size", nullable = false)
    private long size;

    @Column(name = "sequences", nullable = false)
    private long sequences;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

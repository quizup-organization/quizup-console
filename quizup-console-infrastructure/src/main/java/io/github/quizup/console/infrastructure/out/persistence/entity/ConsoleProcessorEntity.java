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
@Table(name = "console_processor")
@Getter
@Setter
@NoArgsConstructor
public class ConsoleProcessorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "application", nullable = false)
    private String application;

    @Column(name = "instance_id", nullable = false)
    private String instanceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "mode", nullable = false)
    private String mode;

    @Column(name = "running", nullable = false)
    private boolean running;

    @Column(name = "error", nullable = false)
    private boolean error;

    @Column(name = "replaying", nullable = false)
    private boolean replaying;

    @Column(name = "supports_reset", nullable = false)
    private boolean supportsReset;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

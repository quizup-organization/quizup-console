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
@Table(name = "console_instance")
@Getter
@Setter
@NoArgsConstructor
public class ConsoleInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "application", nullable = false)
    private String application;

    @Column(name = "instance_id", nullable = false)
    private String instanceId;

    @Column(name = "version")
    private String version;

    /** Adresse `ip:port` de l'instance (ciblage des actions par pod). */
    @Column(name = "address")
    private String address;

    @Column(name = "uptime_seconds", nullable = false)
    private long uptimeSeconds;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;
}

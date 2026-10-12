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

@Entity
@Table(name = "console_segment")
@Getter
@Setter
@NoArgsConstructor
public class ConsoleSegmentEntity {

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

    @Column(name = "segment_id", nullable = false)
    private int segmentId;

    @Column(name = "mask", nullable = false)
    private int mask;

    @Column(name = "caught_up", nullable = false)
    private boolean caughtUp;

    @Column(name = "replaying", nullable = false)
    private boolean replaying;

    @Column(name = "merging", nullable = false)
    private boolean merging;

    @Column(name = "error_state", nullable = false)
    private boolean errorState;

    @Column(name = "current_position")
    private Long currentPosition;

    @Column(name = "reset_position")
    private Long resetPosition;

    @Column(name = "one_part_of", nullable = false)
    private int onePartOf;

    @Column(name = "error_type")
    private String errorType;

    @Column(name = "error_message")
    private String errorMessage;
}

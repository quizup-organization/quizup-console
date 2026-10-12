-- Contenu natif Axon Server (v2) : champs processors/segments repris de l'API interne SDK 4.6.

ALTER TABLE console_processor
    ADD COLUMN can_pause              boolean      not null default false,
    ADD COLUMN can_play               boolean      not null default false,
    ADD COLUMN can_split              boolean      not null default false,
    ADD COLUMN can_merge              boolean      not null default false,
    ADD COLUMN token_store_identifier varchar(255),
    ADD COLUMN active_threads         integer      not null default 0,
    ADD COLUMN capacity               integer      not null default 0,
    ADD COLUMN warnings               text;

ALTER TABLE console_segment
    ADD COLUMN one_part_of integer not null default 1;

-- Adresse ip:port de chaque instance (snapshot agent SDK 4.7) : permet à la console de cibler
-- précisément un pod pour les actions (multi-réplicas).

ALTER TABLE console_instance
    ADD COLUMN address varchar(255);

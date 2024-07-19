USE `texera_db`;

ALTER TABLE workflow
ADD is_published BOOL DEFAULT false;
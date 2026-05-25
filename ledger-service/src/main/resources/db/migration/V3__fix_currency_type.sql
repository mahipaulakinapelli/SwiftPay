-- =====================================================================
-- Switch currency from CHAR(3) -> VARCHAR(3) so Hibernate's Types#VARCHAR
-- mapping for java.lang.String matches the column type during validate.
-- Semantics preserved by the chk_*_currency regex CHECK constraints.
-- =====================================================================

ALTER TABLE accounts     ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE transactions ALTER COLUMN currency TYPE VARCHAR(3);

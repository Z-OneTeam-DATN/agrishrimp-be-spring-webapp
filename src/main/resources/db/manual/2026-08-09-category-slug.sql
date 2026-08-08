-- AgriShrimp SEO category slug patch.
-- Run this before starting profiles that use SPRING_JPA_HIBERNATE_DDL_AUTO=validate.
-- The application also contains an idempotent Java schema patch that backfills
-- unique slug values while avoiding product slug collisions.

SET @category_slug_column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'category'
    AND COLUMN_NAME = 'slug'
);

SET @add_category_slug_sql := IF(
  @category_slug_column_exists = 0,
  'ALTER TABLE category ADD COLUMN slug VARCHAR(180) NULL',
  'SELECT 1'
);

PREPARE add_category_slug_stmt FROM @add_category_slug_sql;
EXECUTE add_category_slug_stmt;
DEALLOCATE PREPARE add_category_slug_stmt;

-- Start with a deterministic fallback. The Java startup patch uses prettier
-- normalized slugs when it runs before validation; this manual fallback keeps
-- legacy rows unique by adding the category id.
UPDATE category
SET slug = CONCAT(
  LOWER(TRIM(BOTH '-' FROM REGEXP_REPLACE(REGEXP_REPLACE(name, '[^[:alnum:]]+', '-'), '-+', '-'))),
  '-',
  id
)
WHERE slug IS NULL OR TRIM(slug) = '';

SET @category_slug_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'category'
    AND INDEX_NAME = 'uq_category_slug'
);

SET @add_category_slug_index_sql := IF(
  @category_slug_index_exists = 0,
  'CREATE UNIQUE INDEX uq_category_slug ON category (slug)',
  'SELECT 1'
);

PREPARE add_category_slug_index_stmt FROM @add_category_slug_index_sql;
EXECUTE add_category_slug_index_stmt;
DEALLOCATE PREPARE add_category_slug_index_stmt;

[SQL_SETUP_DROP_CATEGORY]
DROP TABLE IF EXISTS category;

[SQL_SETUP_DROP_VIEW_CATEGORY_FULL]
DROP VIEW IF EXISTS categoryFull;

[SQL_SETUP_DROP_CATEGORY_RULE]
DROP TABLE IF EXISTS categoryRule;

[SQL_SETUP_CREATE_CATEGORY]
CREATE TABLE category (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  parent_id INTEGER,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(parent_id) REFERENCES category(id) ON DELETE CASCADE,
  CHECK (TRIM(name) <> ''),
  CHECK (parent_id IS NULL OR parent_id <> id));

[SQL_SETUP_CREATE_UNIQUE_INDEX_CATEGORY_ROOT_NAME]
CREATE UNIQUE INDEX IF NOT EXISTS idx_category_root_name ON category (LOWER(TRIM(name))) WHERE parent_id IS NULL;

[SQL_SETUP_CREATE_UNIQUE_INDEX_CATEGORY_PARENT_NAME]
CREATE UNIQUE INDEX IF NOT EXISTS idx_category_parent_name ON category (parent_id, LOWER(TRIM(name))) WHERE parent_id IS NOT NULL;

[SQL_SETUP_CREATE_TRIGGER_CATEGORY_PREVENT_CYCLE_INSERT]
CREATE TRIGGER IF NOT EXISTS prevent_category_cycle_insert
BEFORE INSERT ON category
WHEN NEW.parent_id IS NOT NULL
    AND (NEW.parent_id = NEW.id
      OR EXISTS (
        WITH RECURSIVE ancestors(id, parent_id) AS (
          SELECT id, parent_id
          FROM category
          WHERE id = NEW.parent_id
          UNION ALL
          SELECT c.id, c.parent_id
          FROM category c
          JOIN ancestors a ON c.id = a.parent_id)
        SELECT 1
        FROM ancestors
        WHERE id = NEW.id))
BEGIN
  SELECT RAISE(FAIL, "category parent cycle is not allowed");
END;

[SQL_SETUP_CREATE_TRIGGER_CATEGORY_PREVENT_CYCLE_UPDATE]
CREATE TRIGGER IF NOT EXISTS prevent_category_cycle_update
BEFORE UPDATE OF parent_id ON category
WHEN NEW.parent_id IS NOT NULL
    AND (NEW.parent_id = NEW.id
      OR EXISTS (
        WITH RECURSIVE descendants(id) AS (
          SELECT id
          FROM category
          WHERE parent_id = OLD.id
          UNION ALL
          SELECT c.id
          FROM category c
          JOIN descendants d ON c.parent_id = d.id)
        SELECT 1
        FROM descendants
        WHERE id = NEW.parent_id))
BEGIN
  SELECT RAISE(FAIL, "category parent cycle is not allowed");
END;

[SQL_SETUP_VIEW_CATEGORY_FULL]
CREATE VIEW categoryFull AS WITH RECURSIVE category_tree (id, parent_id, "singleName", name, updatedAt)  AS (
    SELECT cg.id, cg.parent_id, cg.name, cg.name, cg.updatedAt
    FROM category cg
    WHERE cg.parent_id IS NULL
    UNION ALL
    SELECT cg.id, cg.parent_id, cg.name, CONCAT(ct.name, ':', cg.name), cg.updatedAt
    FROM category cg
    JOIN category_tree ct ON cg.parent_id = ct.id)
  SELECT id, parent_id, singleName as name, name AS fullName, updatedAt FROM category_tree;

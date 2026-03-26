[SQL_SETUP_DROP_CATEGORY]
DROP TABLE IF EXISTS category;

[SQL_SETUP_DROP_VIEW_CATEGORY_FULL]
DROP VIEW IF EXISTS categoryFull;

[SQL_SETUP_DROP_CATEGORY_RULE]
DROP TABLE IF EXISTS categoryRule;

[SQL_SETUP_CREATE_CATEGORY]
CREATE TABLE category (
  id INTEGER PRIMARY KEY,
  name TEXT,
  parent_id INTEGER,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(parent_id) REFERENCES category(id) ON DELETE CASCADE);

[SQL_SETUP_VIEW_CATEGORY_FULL]
CREATE VIEW categoryFull AS WITH RECURSIVE category_tree (id, parent_id, "singleName", name, updatedAt)  AS (
    SELECT id, parent_id, name, name, updatedAt
    FROM category
    WHERE parent_id IS NULL
    UNION ALL
    SELECT c.id, c.parent_id, c.name, CONCAT(ct.name, ':', c.name), c.updatedAt
    FROM category c
    JOIN category_tree ct ON c.parent_id = ct.id)
  SELECT id, parent_id, singleName as name, name AS fullName, updatedAt FROM category_tree;

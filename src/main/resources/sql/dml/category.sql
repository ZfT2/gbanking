[SQL_SELECT_ALL_CATEGORIES_BASE]
SELECT id, parent_id, name, null AS fullName, updatedAt FROM category;

[SQL_SELECT_ALL_CATEGORIES]
${SQL_SELECT_ALL_CATEGORIES_BASE};

[SQL_FIND_CATEGORY]
${SQL_SELECT_ALL_CATEGORIES_BASE} WHERE name LIKE ? AND (parent_id = ? OR parent_id IS NULL);

[SQL_SELECT_CATEGORY_RECURSIVE_WITH]
WITH RECURSIVE category_tree (id, parent_id, "singleName", name, updatedAt)  AS (
    SELECT id, parent_id, name, name, updatedAt
    FROM category
    WHERE parent_id IS NULL
    UNION ALL
    SELECT c.id, c.parent_id, c.name, CONCAT(ct.name, ':', c.name), c.updatedAt
    FROM category c
    JOIN category_tree ct ON c.parent_id = ct.id)

[SQL_SELECT_ALL_CATEGORIES_FULL]
${SQL_SELECT_CATEGORY_RECURSIVE_WITH} SELECT id, parent_id, singleName as name, name AS fullName, updatedAt FROM category_tree;

[SQL_SELECT_CATEGORY_FULL_BY_NAME]
${SQL_SELECT_ALL_CATEGORIES_FULL} WHERE name like ?;

[SQL_SELECT_ID_CATEGORY_BY_NAME]
SELECT id FROM category WHERE name = ?;

[SQL_INSERT_CATEGORY]
INSERT INTO category (name, parent_id, updatedAt) VALUES (?, ?, ?);

[SQL_UPDATE_CATEGORY]
UPDATE category SET name = ?, parent_id = ?, updatedAt = ? WHERE id = ?;

[SQL_DELETE_CATEGORY]
DELETE FROM category WHERE id = ?;

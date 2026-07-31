[SQL_SELECT_ALL_CATEGORIES_BASE]
SELECT cg.id, cg.parent_id, cg.name, null AS fullName, cg.updatedAt FROM category cg;

[SQL_FIND_CATEGORY]
${SQL_SELECT_ALL_CATEGORIES_BASE} WHERE TRIM(cg.name) = TRIM(?) COLLATE NOCASE AND cg.parent_id IS ?;

[SQL_SELECT_CATEGORY_RECURSIVE_WITH]
WITH RECURSIVE category_tree (id, parent_id, "singleName", name, updatedAt)  AS (
    SELECT cg.id, cg.parent_id, cg.name, cg.name, cg.updatedAt
    FROM category cg
    WHERE cg.parent_id IS NULL
    UNION ALL
    SELECT cg.id, cg.parent_id, cg.name, CONCAT(ct.name, ':', cg.name), cg.updatedAt
    FROM category cg
    JOIN category_tree ct ON cg.parent_id = ct.id)

[SQL_SELECT_ALL_CATEGORIES_FULL]
${SQL_SELECT_CATEGORY_RECURSIVE_WITH} SELECT id, parent_id, singleName as name, name AS fullName, updatedAt FROM category_tree;

[SQL_SELECT_ID_CATEGORY_BY_NAME]
SELECT cg.id FROM category cg WHERE cg.name = ?;

[SQL_INSERT_CATEGORY]
INSERT INTO category (name, parent_id, updatedAt) VALUES (?, ?, ?);

[SQL_UPDATE_CATEGORY]
UPDATE category SET name = ?, parent_id = ?, updatedAt = ? WHERE id = ?;

[SQL_DELETE_CATEGORY]
DELETE FROM category WHERE id = ?;

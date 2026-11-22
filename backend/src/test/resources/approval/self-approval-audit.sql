-- Read-only candidates: current line/history evidence and CURRENT organization, not historical proof.
WITH RECURSIVE candidates AS (
    SELECT d.id, d.doc_no, d.drafter_id, u.department_id
    FROM approval_documents d
    JOIN users u ON u.id = d.drafter_id
    WHERE d.status = 'APPROVED'
      AND NOT EXISTS (
          SELECT 1 FROM approval_lines l
          WHERE l.document_id = d.id AND l.line_kind = 'APPROVER'
            AND (l.status IS NULL OR l.status <> 'SKIPPED' OR l.acted_at IS NOT NULL)
      )
      AND NOT EXISTS (
          SELECT 1 FROM approval_histories h
          WHERE h.document_id = d.id AND h.action IN ('APPROVE', 'REJECT')
      )
), department_chain AS (
    SELECT c.id AS document_id, c.drafter_id, dep.id AS department_id,
           dep.parent_id, dep.head_user_id, head.status AS head_status,
           CAST(CONCAT(',', dep.id, ',') AS CHAR(4096)) AS visited, 0 AS cycle
    FROM candidates c
    LEFT JOIN departments dep ON dep.id = c.department_id
    LEFT JOIN users head ON head.id = dep.head_user_id
    UNION ALL
    SELECT chain.document_id, chain.drafter_id, dep.id,
           dep.parent_id, dep.head_user_id, head.status,
           CONCAT(chain.visited, dep.id, ','),
           CASE WHEN LOCATE(CONCAT(',', dep.id, ','), chain.visited) > 0 THEN 1 ELSE 0 END
    FROM department_chain chain
    JOIN departments dep ON dep.id = chain.parent_id
    LEFT JOIN users head ON head.id = dep.head_user_id
    WHERE chain.parent_id IS NOT NULL AND chain.cycle = 0
)
SELECT c.id, c.doc_no, c.drafter_id
FROM candidates c
WHERE EXISTS (
    SELECT 1 FROM department_chain chain
    WHERE chain.document_id = c.id
      AND (chain.department_id IS NULL OR chain.head_user_id IS NULL
           OR chain.head_user_id <> chain.drafter_id
           OR COALESCE(chain.head_status, '') <> 'ACTIVE' OR chain.cycle = 1)
)
OR NOT EXISTS (
    SELECT 1 FROM department_chain chain
    WHERE chain.document_id = c.id AND chain.department_id IS NOT NULL AND chain.parent_id IS NULL
)
ORDER BY c.id

---
name: sql-best-practices
description: Best practices for writing clear, efficient and maintainable SQL queries.
---

# Purpose

Write SQL that is:

- Correct
- Readable
- Efficient
- Maintainable

Prefer clarity over clever solutions.

---

# General Principles

- Write standard SQL whenever possible.
- Avoid vendor-specific features unless required.
- Keep queries simple and easy to understand.
- Format SQL consistently.

---

# Query Design

- Select only the columns that are needed.
- Avoid `SELECT *` unless explicitly requested.
- Filter data as early as possible.
- Use meaningful aliases.
- Prefer explicit `JOIN` syntax.

---

# Filtering

- Use `WHERE` to reduce result sets.
- Handle `NULL` values explicitly.
- Avoid unnecessary conditions.

---

# Joins

Use the appropriate join type:

- INNER JOIN
- LEFT JOIN
- RIGHT JOIN (only when justified)
- FULL JOIN (only when supported and required)

Never use implicit joins.

---

# Aggregation

Use:

- GROUP BY
- HAVING

only when required.

Aggregate only the necessary data.

---

# Performance

Prefer queries that can use indexes.

Avoid:

- unnecessary subqueries
- repeated calculations
- excessive sorting

Optimize only when there is a measurable benefit.

---

# Transactions

Use transactions when multiple operations must succeed together.

Keep transactions as short as possible.

Avoid long-running transactions.

---

# Data Modification

When writing:

- UPDATE
- DELETE

always ensure the target rows are clearly identified.

Never generate destructive statements without a WHERE clause unless explicitly requested.

---

# Schema Design

Prefer:

- clear table names
- meaningful column names
- primary keys
- foreign keys
- appropriate constraints

Avoid duplicated data when normalization is appropriate.

---

# Indexes

Create indexes only for frequently searched or joined columns.

Avoid excessive indexing.

Remember that indexes improve reads but increase write cost.

---

# Security

Never concatenate user input into SQL.

Always recommend parameterized queries.

Avoid dynamic SQL unless absolutely necessary.

---

# Readability

- Indent consistently.
- Place each selected column on its own line for larger queries.
- Keep complex queries well organized.
- Use Common Table Expressions (CTEs) when they improve readability.

---

# Documentation

Comment only when the intent is not obvious.

Avoid comments that simply repeat the SQL.

---

# Rule

Choose the simplest SQL solution that is correct, readable and performs well.
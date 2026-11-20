# 0002 — UUID v7 for every new id

- Date: 2026-11-21
- Status: Accepted

## Context
Ids were `UUID.randomUUID()` (v4). Random keys scatter inserts across the primary-key B-tree; RFC 9562 v7 puts a 48-bit millisecond timestamp first, so new keys append and ids sort by creation time. JDK 25 has no v7 factory, and Postgres 16 has no `uuidv7()`.

## Decision
`common.UuidV7.generate()` creates every new primary key and event id. It is a small in-repo class, not a library, so generated services carry it with no new dependency. Existing ids are left as they are: the columns are already `uuid`, and v4 and v7 values coexist.

## Consequences
- New rows get time-ordered ids; old rows keep their v4 ids, so URLs and cross-service references stay valid.
- An id now reveals its creation time to the millisecond and carries 74 random bits instead of 122. That is acceptable because ids are identifiers, never secrets: never use an id as a token, share link or proof of possession.
- Not unique-per-millisecond ordered: ids created in the same millisecond sort randomly among themselves.

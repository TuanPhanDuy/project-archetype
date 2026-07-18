<!--
  PRD template — copy this to PRD.md at the repo root and fill it in.
  /prd-to-jira parses this exact structure:
    H2 "## Epic: <title>"     -> one Jira Epic
    H3 "### Story: <title>"   -> one Jira Story, linked to the Epic above it
    "**Subtasks:**" checklist -> one Jira Subtask per `- [ ]` line, linked to the Story
  Keep the heading levels and the "Epic:" / "Story:" prefixes exact — the parser
  matches on them literally. HTML comments like `<!-- jira: PROJ-123 -->` are written
  back by the command after creation; don't remove them on subsequent edits, or the
  next run will create duplicates instead of detecting the existing issue.
-->

# PRD: <Product / Release Name>

One-paragraph summary of what this release accomplishes and why.

## Epic: <Epic title>

**Description:** What this epic delivers and the business value.

### Story: <Story title>

As a `<role>`, I want `<capability>`, so that `<benefit>`.

**Acceptance Criteria:**
- Given `<context>`, When `<action>`, Then `<outcome>`.
- Given `<context>`, When `<action>`, Then `<outcome>`.

**Subtasks:**
- [ ] `<implementation subtask>`
- [ ] `<test subtask>`
- [ ] `<docs subtask>`

### Story: <Another story title>

As a `<role>`, I want `<capability>`, so that `<benefit>`.

**Acceptance Criteria:**
- Given `<context>`, When `<action>`, Then `<outcome>`.

**Subtasks:**
- [ ] `<implementation subtask>`

## Epic: <Another epic title>

**Description:** ...

### Story: <Story title>

As a `<role>`, I want `<capability>`, so that `<benefit>`.

**Acceptance Criteria:**
- Given `<context>`, When `<action>`, Then `<outcome>`.

**Subtasks:**
- [ ] `<implementation subtask>`

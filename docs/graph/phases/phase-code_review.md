---
tags: [phase]
generated: true
---
# phase · Code Review

- **Inputs:** git diff; tests
- **Outputs:** findings
- **Entry Gate:** Testing exit_gate met, and a PR is open from the feature branch (push the branch, open the PR against main) — /review-pr operates on an existing PR, it doesn't create one.
- **Exit Gate:** No blocking findings remain.
- **Owner:** [[code-reviewer]]
- **Next:** [[phase-security_review]]

---
tags: [phase]
generated: true
---
# phase · Team Lead Approval

- **Inputs:** requirements; all prior phase verdicts
- **Outputs:** go/no-go verdict
- **Entry Gate:** Code Review exit_gate met, and Security Review's too for any tier that runs it (check the task's workflow_profile, don't assume Standard).
- **Exit Gate:** Verdict is APPROVED FOR MERGE — acceptance criteria genuinely met and every prior gate verified, not just claimed.
- **Owner:** [[team-lead]]
- **Next:** [[phase-release]]

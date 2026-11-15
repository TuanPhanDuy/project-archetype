---
tags: [phase]
generated: true
---
# phase · Work Planning & Orchestration

- **Inputs:** design; work breakdown
- **Outputs:** independent work packages; dependency graph; dispatched + merged changes; status report to scrum-master
- **Entry Gate:** Design exit_gate met; the work is wide enough that parallel dispatch is worth the coordination cost.
- **Exit Gate:** Every requirement maps to a package; parallel-safe packages share no files/symbols/ migrations; sequential packages have an explicit required order; every dispatched package is merged, verified, and its status reported to scrum-master.
- **Owner:** [[work-planner]]
- **Next:** [[phase-implementation]]

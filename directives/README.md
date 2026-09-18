# Directives (Layer 1)

This directory contains Standard Operating Procedures (SOPs) written in Markdown, as defined by the 3-Layer Architecture in [AGENTS.md](file:///c:/Users/yashw/Documents/GitHub/android_studio/Checker-Tic/AGENTS.md).

## Purpose
Directives define **what to do**:
- Goals and scope
- Inputs and prerequisites
- Deterministic tools/scripts to call (in `execution/`)
- Expected outputs and deliverables
- Edge cases and failure modes

## Directive Template
Every directive should follow this structure:

```markdown
# Directive: [Task Name]

## Goal
[Clear statement of what this procedure accomplishes]

## Inputs
- [Required environment variables, files, arguments]

## Tools / Scripts
- [Script path in execution/, e.g., execution/my_task.py]

## Outputs
- [Expected output files, formats, or cloud deliverables]

## Edge Cases & Error Handling
- [Known rate limits, error states, self-annealing notes]
```

## Principles
- **Living documents**: Update directives as you learn new constraints, edge cases, or API updates.
- **Preserve directives**: Never overwrite or delete directives without explicit instructions.

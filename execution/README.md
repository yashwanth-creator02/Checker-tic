# Execution (Layer 3)

This directory contains deterministic Python scripts that perform the actual work, as defined by the 3-Layer Architecture in [AGENTS.md](file:///c:/Users/yashw/Documents/GitHub/android_studio/Checker-Tic/AGENTS.md).

## Purpose
Execution scripts handle **doing the work**:
- API calls and network operations
- Data processing and transformations
- File operations and formatting
- Database interactions

## Principles
1. **Deterministic & Testable**: Scripts must be reliable, fast, well-commented, and produce consistent results.
2. **Environment Configuration**: Store all API tokens, credentials, and environment configurations in `.env` (never hardcoded).
3. **Intermediates in `.tmp/`**: Store intermediate/temporary files in `.tmp/` (never commit them; they can always be regenerated).
4. **Self-annealing**: When a script encounters an error or API constraint:
   - Read the error and stack trace.
   - Fix and test the script.
   - Update the corresponding directive in `directives/` with the learnings.

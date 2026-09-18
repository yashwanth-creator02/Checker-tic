<!-- brand-leo-utils:skill-sync:start -->
## Skills (brand-leo-utils)

This project's skills live in `.agent/skills/` — gitignored here. The
brand-leo-utils repo is the source of truth; this folder is a disposable
local copy, and it belongs to whichever agent is handling the session,
not one specific tool.

- To load or refresh skills from brand-leo-utils, follow its
  `directives/instantiate_project_skills.md`.
- If you create or edit a skill under `.agent/skills/`, it isn't done
  until it's synced back: follow brand-leo-utils's
  `directives/sync_skill_to_brand.md`. Both directives branch first,
  never commit to main/master directly, and only reach main via a
  pull request or an explicit, separate go-ahead from the user.
<!-- brand-leo-utils:skill-sync:end -->

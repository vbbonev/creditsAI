---
name: exam-log
description: Record the work just done in docs/ai-log.md under the right module, in the exact structure the course report needs (approach, workflow, testing, AI tool, key prompts). Use when the user runs /exam-log or after finishing a module.
argument-hint: [module name]
disable-model-invocation: true
---

# Log work for the course report

The exam document (see `docs/Project-Assignment.docx`) needs these sections per module: approach & reasoning, step-by-step workflow, testing strategy, AI tool choice, and 2–3 key prompts. This skill collects that material as you go.

Module: `$ARGUMENTS`. If it's empty, infer it from the work in this conversation. Use one of: Infrastructure, Database layer, Authentication, Backend API, Frontend, Testing.

## Steps
1. Read `docs/ai-log.md`. If it doesn't exist, create it with an `# AI log` header and one `## <Module>` heading per module above.
2. Look at this conversation and at the last ~30 lines of `docs/prompts.log` (written by the UserPromptSubmit hook) to find what was actually done.
3. Append an entry under the matching `## <Module>` heading:

```markdown
### <YYYY-MM-DD> — <short title>
- **Approach:** <1–2 sentences on why it was solved this way>
- **Workflow:** <numbered steps: what was asked, which subagent/skill/rule/hook was involved>
- **Testing:** <tests written/run and their result>
- **AI tooling used:** <e.g. Claude Code: /new-endpoint skill, backend-dev + test-writer subagents, liquibase + jpa-repositories rules>
- **Key prompt(s):**
  > <exact prompt text, trimmed>
- **Issues / fixes:** <where the AI got it wrong and how it was corrected — good material for "Challenges">
- **Evidence:** <screenshot file names in docs/screenshots/, or commands to reproduce>
```

4. Be factual. Only log things that happened in this session. Keep each entry under ~15 lines.
5. Show the user the entry you added.

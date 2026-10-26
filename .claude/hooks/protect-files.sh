#!/usr/bin/env bash
# PreToolUse hook (Edit|Write|MultiEdit): enforce project rules that must never be broken.
#  - no edits to secrets (.env)
#  - no edits to existing Liquibase changesets (checksums would break)
#  - no native SQL in Java code (JPQL / derived queries only)
#  - no physical deletes in production Java code (soft delete via archive)
#  - no reactive forms in the Angular frontend (template-driven only)
# Exit code 2 blocks the tool call and feeds stderr back to Claude.
set -euo pipefail

input=$(cat)
tool=$(jq -r '.tool_name' <<<"$input")
file=$(jq -r '.tool_input.file_path // empty' <<<"$input")
[[ -z "$file" ]] && exit 0

case "$file" in
  */.env|*/.env.*)
    [[ "$file" == *.env.example ]] && exit 0
    echo "Blocked: $file holds secrets. Ask the user to edit it manually." >&2
    exit 2
    ;;
  */db/changelog/changes/*.sql)
    if [[ -e "$file" ]]; then
      echo "Blocked ($tool): $file is an existing Liquibase changeset; editing it breaks checksums. Add a new changes/NNN-*.sql file instead (see .claude/rules/liquibase.md)." >&2
      exit 2
    fi
    ;;
  *.java)
    # New text being written: Write -> content, Edit -> new_string, MultiEdit -> edits[].new_string
    new_text=$(jq -r '[.tool_input.content, .tool_input.new_string, (.tool_input.edits // [] | .[].new_string)] | map(select(. != null)) | join("\n")' <<<"$input")
    if grep -Eq 'nativeQuery[[:space:]]*=[[:space:]]*true|createNativeQuery|@NativeQuery' <<<"$new_text"; then
      echo "Blocked: native SQL is not allowed in $file. Use a derived query method or @Query with JPQL (see .claude/rules/jpa-repositories.md)." >&2
      exit 2
    fi
    # Soft delete only: no physical deletes in production code (tests may clean up)
    if [[ "$file" == */src/main/java/* ]] && grep -Eiq 'repository\.delete[a-z]*\(|delete[[:space:]]+from[[:space:]]' <<<"$new_text"; then
      echo "Blocked: physical deletes are not allowed in $file. Archive the record instead (set archived = true via the entity's archive() method; see 'Archiving' in backend/CLAUDE.md)." >&2
      exit 2
    fi
    ;;
  */frontend/src/*.ts)
    new_text=$(jq -r '[.tool_input.content, .tool_input.new_string, (.tool_input.edits // [] | .[].new_string)] | map(select(. != null)) | join("\n")' <<<"$input")
    if grep -Eq 'ReactiveFormsModule|FormBuilder|FormGroup|FormControl|FormArray' <<<"$new_text"; then
      echo "Blocked: reactive forms are not allowed in $file. Use template-driven forms (FormsModule + [(ngModel)], validators in the template, custom validator directives; see frontend/CLAUDE.md)." >&2
      exit 2
    fi
    ;;
esac
exit 0

#!/usr/bin/env bash
# UserPromptSubmit hook: append every prompt to docs/prompts.log.
# This is raw material for the "Key prompts" sections of the course report (/exam-log reads it).
input=$(cat)
root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
mkdir -p "$root/docs"
{
  printf '\n--- %s\n' "$(date '+%Y-%m-%d %H:%M:%S')"
  jq -r '.prompt' <<<"$input"
} >> "$root/docs/prompts.log"
exit 0

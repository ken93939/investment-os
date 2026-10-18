# Issue tracker: GitHub

Issues and specs live in GitHub Issues for `ken93939/investment-os`.
Use the authenticated `gh` CLI with `--repo ken93939/investment-os`.

## Operations

- Create issues with `gh issue create`; supply multiline content through
  `--body-file`.
- Read issues with `gh issue view <number> --json number,title,body,labels,comments`.
- List issues with `gh issue list`, filtering by state and configured labels.
- Apply or remove labels with `gh issue edit`.
- Comment with `gh issue comment`; use `--body-file` for multiline content.
- Close issues with `gh issue close`.

When a skill says “publish to the issue tracker,” create a GitHub issue.
When it says “fetch the ticket,” read its full body and relevant comments.

## Relationships and wayfinding

Use native GitHub sub-issues for parent/child relationships and native issue
dependencies for blockers. Use supported CLI commands or `gh api`.

If those features are unavailable, record `Part of #<parent>` and
`Blocked by: #<blocker>` in child issues and list children in the parent.

A wayfinder map uses `wayfinder:map`; its decision tickets use
`wayfinder:research`, `wayfinder:prototype`, `wayfinder:grilling`, or
`wayfinder:task`. Create these labels when that workflow is introduced.

The frontier consists of open, unassigned children whose blockers are closed.
Claim a ticket by assigning it to the driving developer. Resolve it by
recording the answer, closing it, and linking the resolution from the map.

## Pull requests as a triage surface

PRs as a request surface: no.

Implementation pull requests remain governed by AGENTS.md.

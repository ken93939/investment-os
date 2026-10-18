# Domain documentation

Use a single domain documentation context across the backend and frontend.
Separate builds and capability packages do not require separate glossaries.

Before exploring an affected area:

1. Read the root GLOSSARY.md if present.
2. Read relevant decisions under docs/adr/ if present.
3. Follow the project-context and architecture pointers in AGENTS.md.

Proceed normally when glossary or ADR files are absent. Create them lazily
through domain-modeling when terminology or architectural decisions are resolved.

Use glossary terms consistently in issues, designs, code discussions, and tests.
Keep implementation details out of the glossary.

Surface conflicts with existing ADRs explicitly before proposing a replacement.
A future GLOSSARY-MAP.md may introduce multiple contexts if the domain needs them.

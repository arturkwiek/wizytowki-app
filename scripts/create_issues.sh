#!/usr/bin/env bash
# Tworzy etykiety i issues z docs/ROADMAP.md. Wymaga: gh (GitHub CLI), zalogowanego: gh auth login
set -euo pipefail
cd "$(dirname "$0")/.."
for i in 0 1 2 3 4 5; do gh label create "etap-$i" --force --color "1D76DB" >/dev/null; done
gh label create zadanie --force --color "0E8A16" >/dev/null
stage=""
while IFS= read -r line; do
  if [[ $line =~ ^##\ Etap\ ([0-9]+) ]]; then stage="${BASH_REMATCH[1]}"; title_prefix="${line#\#\# }"
  elif [[ $line =~ ^-\ (.+) && -n $stage ]]; then
    task="${BASH_REMATCH[1]}"
    body="Etap: ${title_prefix}

Szczegóły: docs/ROADMAP.md i REQUIREMENTS.md. Zasady: AGENTS.md.

## Kryteria akceptacji
- [ ] ${task}
- [ ] CI zielone, testy dodane"
    gh issue create --title "[Etap ${stage}] ${task}" --body "$body" --label "etap-${stage}" --label zadanie
  fi
done < docs/ROADMAP.md
echo "Gotowe."

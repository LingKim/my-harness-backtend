#!/usr/bin/env bash

set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

required_files=(
  "AGENTS.md"
  "../.codex/rules/backend-conventions.md"
  "../.codex/rules/database-conventions.md"
  "../.codex/skills/java-springboot/SKILL.md"
  "../.codex/skills/mysql/SKILL.md"
  "../.codex/skills/mysql/references/explain-analysis.md"
  "../.codex/skills-lock.json"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "失败：后端 AI 治理缺少文件：$file" >&2
    exit 1
  fi
done

if ! grep -Fq '../.codex/rules/backend-conventions.md' AGENTS.md || ! grep -Fq '../.codex/rules/database-conventions.md' AGENTS.md; then
  echo "失败：后端 AGENTS.md 必须路由到两份 conventions" >&2
  exit 1
fi

if ! grep -Fq 'java-springboot' AGENTS.md || ! grep -Fq 'mysql' AGENTS.md; then
  echo "失败：后端 AGENTS.md 必须路由到 Java 与 MySQL Skills" >&2
  exit 1
fi

node -e '
const fs = require("fs");
const text = fs.readFileSync("AGENTS.md", "utf8");
const paths = [...text.matchAll(/\.\.\/\.codex\/rules\/[a-z0-9-]+\.md/g)].map((match) => match[0]);
const missing = [...new Set(paths)].filter((path) => !fs.existsSync(path));
if (missing.length > 0) {
  console.error(`失效 Rule 引用：${missing.join(", ")}`);
  process.exit(1);
}
' || {
  echo "失败：后端 AGENTS.md 包含失效 Rule 引用" >&2
  exit 1
}

for rule_id in RULE-BE-001 RULE-BE-002 RULE-BE-003 RULE-BE-004 RULE-BE-005 RULE-BE-006 RULE-BE-007 RULE-BE-008; do
  if ! grep -Fq "$rule_id" ../.codex/rules/backend-conventions.md; then
    echo "失败：backend-conventions.md 缺少 Rule ID：$rule_id" >&2
    exit 1
  fi
done

for rule_id in RULE-DB-001 RULE-DB-002 RULE-DB-003 RULE-DB-004 RULE-DB-005 RULE-DB-006 RULE-DB-007 RULE-DB-008; do
  if ! grep -Fq "$rule_id" ../.codex/rules/database-conventions.md; then
    echo "失败：database-conventions.md 缺少 Rule ID：$rule_id" >&2
    exit 1
  fi
done

java_hash="$(
  cd ../.codex/skills/java-springboot
  find . -type f -print0 | LC_ALL=C sort -z | xargs -0 shasum -a 256 | shasum -a 256 | awk '{print $1}'
)"
mysql_hash="$(
  cd ../.codex/skills/mysql
  find . -type f -print0 | LC_ALL=C sort -z | xargs -0 shasum -a 256 | shasum -a 256 | awk '{print $1}'
)"

node -e '
const fs = require("fs");
const lock = JSON.parse(fs.readFileSync("../.codex/skills-lock.json", "utf8"));
const names = Object.keys(lock.skills ?? {}).sort();
const java = lock.skills?.["java-springboot"];
const mysql = lock.skills?.mysql;
const valid = lock.version === 1
  && JSON.stringify(names) === JSON.stringify(["java-springboot", "mysql", "vercel-react-best-practices"])
  && java?.source === "github/awesome-copilot"
  && java?.sourceType === "github"
  && java?.skillPath === "skills/java-springboot/SKILL.md"
  && /^[0-9a-f]{64}$/.test(java?.computedHash ?? "")
  && java?.contentHash === process.argv[1]
  && mysql?.source === "planetscale/database-skills"
  && mysql?.sourceType === "github"
  && mysql?.skillPath === "skills/mysql/SKILL.md"
  && /^[0-9a-f]{64}$/.test(mysql?.computedHash ?? "")
  && mysql?.contentHash === process.argv[2];
process.exit(valid ? 0 : 1);
' "$java_hash" "$mysql_hash" || {
  echo "失败：后端 Skill 锁记录或内容哈希不匹配" >&2
  exit 1
}

for forbidden in .codex .agents .claude CLAUDE.md rules skills-lock.json; do
  if [[ -e "$forbidden" ]]; then
    echo "失败：后端不得保存局部治理路径：$forbidden" >&2
    exit 1
  fi
done

echo "通过：后端入口、集中 Rules 与 Skills 治理结构有效"

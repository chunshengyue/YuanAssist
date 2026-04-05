#!/usr/bin/env bash
set -euo pipefail

# 进入项目根目录，保证相对路径一致
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR%/scripts}"
cd "$PROJECT_ROOT"

# KISS: 只保留编译、复制、运行的最小步骤
./gradlew bootJar

# 找到最新的可运行 JAR，过滤掉 "-plain" 版本
TARGET_JAR=$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print | sort | tail -n 1)
if [[ -z "$TARGET_JAR" ]]; then
  echo "未找到可运行的 JAR 包，请确认编译是否成功" >&2
  exit 1
fi

JAR_NAME="$(basename "$TARGET_JAR")"
cp "$TARGET_JAR" "$PROJECT_ROOT/$JAR_NAME"

echo "已复制 $JAR_NAME 到项目根目录"

# 如果缺少 prod 配置，则复制一份模板方便上线
if [[ ! -f "$PROJECT_ROOT/application-prod.yml" ]]; then
  TEMPLATE_FILE="$PROJECT_ROOT/build/resources/main/application-template.yml"
  if [[ -f "$TEMPLATE_FILE" ]]; then
    cp "$TEMPLATE_FILE" "$PROJECT_ROOT/application-prod.yml"
    echo "已生成 application-prod.yml，请根据实际环境修改配置"
  else
    echo "缺少 application-prod.yml，且模板不存在，请手动准备配置文件" >&2
  fi
fi

# 按 prod profile 启动
java -jar "$PROJECT_ROOT/$JAR_NAME" --spring.profiles.active=prod

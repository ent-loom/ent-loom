#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SITE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

DEPLOY_HOST="${DEPLOY_HOST:-my4g}"
DEPLOY_DIR="${DEPLOY_DIR:-/var/www/ent-loom-docs}"
STAGING_DIR="${DEPLOY_STAGING_DIR:-/var/tmp/ent-loom-docs-staging}"
SKIP_INSTALL=false
DRY_RUN=false

usage() {
  cat <<'EOF'
用法：./scripts/deploy.sh [--skip-install] [--dry-run]

选项：
  --skip-install  跳过 npm ci，复用现有 node_modules
  --dry-run       完成构建并预览 rsync 变更，不更新线上目录
  -h, --help      显示帮助

环境变量：
  DEPLOY_HOST         SSH 主机，默认 my4g
  DEPLOY_DIR          线上目录，默认 /var/www/ent-loom-docs
  DEPLOY_STAGING_DIR  远端暂存目录，默认 /var/tmp/ent-loom-docs-staging
EOF
}

while (($# > 0)); do
  case "$1" in
    --skip-install)
      SKIP_INSTALL=true
      ;;
    --dry-run)
      DRY_RUN=true
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      echo "未知参数：$1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

for command_name in node npm ssh rsync; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "缺少命令：${command_name}" >&2
    exit 1
  fi
done

required_node_major="$(tr -d '[:space:]' < "${SITE_DIR}/.node-version" | cut -d. -f1)"
actual_node_major="$(node --version | sed -E 's/^v([0-9]+).*/\1/')"
if [[ "${actual_node_major}" != "${required_node_major}" ]]; then
  echo "Node.js 主版本不匹配：需要 ${required_node_major}，当前 ${actual_node_major}" >&2
  exit 1
fi

cd "${SITE_DIR}"

if [[ "${SKIP_INSTALL}" == false ]]; then
  echo "==> 安装锁定依赖"
  npm ci
fi

echo "==> 检查类型并构建"
npm run typecheck
npm run build

if [[ ! -s build/index.html ]]; then
  echo "构建产物缺少 build/index.html" >&2
  exit 1
fi

echo "==> 检查远端目录"
ssh "${DEPLOY_HOST}" bash -s -- "${STAGING_DIR}" "${DEPLOY_DIR}" <<'REMOTE'
set -eu
install -d -m 0755 "$1" "$2"
REMOTE

rsync_options=(
  --archive
  --compress
  --delete
  --delay-updates
  --human-readable
  --itemize-changes
)

if [[ "${DRY_RUN}" == true ]]; then
  echo "==> 预览增量上传"
  rsync "${rsync_options[@]}" --dry-run build/ "${DEPLOY_HOST}:${STAGING_DIR}/"
  echo "预览完成，未更新线上目录。"
  exit 0
fi

echo "==> 增量上传到暂存目录"
rsync "${rsync_options[@]}" build/ "${DEPLOY_HOST}:${STAGING_DIR}/"

echo "==> 发布到线上目录"
ssh "${DEPLOY_HOST}" bash -s -- "${STAGING_DIR}" "${DEPLOY_DIR}" <<'REMOTE'
set -eu
staging_dir="$1"
deploy_dir="$2"

test -s "${staging_dir}/index.html"
rsync --archive --delete --delay-updates "${staging_dir}/" "${deploy_dir}/"
find "${deploy_dir}" -type d -exec chmod 755 {} +
find "${deploy_dir}" -type f -exec chmod 644 {} +
test -s "${deploy_dir}/index.html"
REMOTE

echo "部署完成：https://ent-loom.lizubin.online/"

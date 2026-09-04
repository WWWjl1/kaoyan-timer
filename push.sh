#!/usr/bin/env bash
# Git Bash / Mac / Linux 一键推送（推荐用这个，不受 Windows 编码影响）
cd "$(dirname "$0")"

command -v git >/dev/null 2>&1 || { echo "[ERROR] git not found. Install https://git-scm.com"; exit 1; }

[ -d .git ] || { echo "[1/5] git init"; git init; } || exit 1
git remote get-url origin >/dev/null 2>&1 \
  && git remote set-url origin https://github.com/WWWjl1/kaoyan-timer.git \
  || git remote add origin https://github.com/WWWjl1/kaoyan-timer.git
echo "[3/5] stage"; git add -A
echo "[4/5] commit"; git commit -m "update: build"
echo "[5/5] push"; git push --force -u origin main

echo "DONE - GitHub Actions is building now."

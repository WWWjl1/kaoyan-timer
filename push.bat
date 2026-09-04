@echo off
setlocal
cd /d "%~dp0"

echo ====================================
echo   Push to GitHub (kaoyan-timer)
echo ====================================

where git >nul 2>nul
if errorlevel 1 goto nogit

if not exist .git (
    echo [1/5] init repo
    git init
)

git remote get-url origin >nul 2>nul
if errorlevel 1 (
    echo [2/5] add remote
    git remote add origin https://github.com/WWWjl1/kaoyan-timer.git
) else (
    git remote set-url origin https://github.com/WWWjl1/kaoyan-timer.git
    echo [2/5] remote reset
)

git branch -M main
echo [3/5] stage files
git add -A
echo [4/5] commit
git commit -m "update: build"
echo [5/5] push
git push --force -u origin main

echo.
echo DONE - GitHub Actions is building now. Check the Actions page.
pause
goto :eof

:nogit
echo [ERROR] git not found. Install from https://git-scm.com
pause

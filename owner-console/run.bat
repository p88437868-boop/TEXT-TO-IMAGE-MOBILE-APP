@echo off
setlocal
rem Starts the console from source (for the owner's own PC).
rem Customers get dist\owner-console\TextPicOwner.exe instead.
cd /d "%~dp0"

set "PY="
where python >nul 2>nul && set "PY=python"
if not defined PY if exist "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" set "PY=%LOCALAPPDATA%\Programs\Python\Python312\python.exe"
if not defined PY goto nopython

"%PY%" run.py
if errorlevel 1 pause
exit /b 0

:nopython
echo Python 3.12 is not installed (or not on PATH).
echo Install it from https://python.org or run: winget install Python.Python.3.12
echo Then: python -m pip install -r requirements.txt
pause
exit /b 1

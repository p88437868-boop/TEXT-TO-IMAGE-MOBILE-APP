@echo off
rem Build, publish and verify the next TextPic Studio version.
rem Extra options are passed through, e.g.  "Release New Version.bat" --draft
setlocal
cd /d "%~dp0"

set "PY=python"
where py >nul 2>nul && set "PY=py -3"
%PY% -c "import sys" >nul 2>nul
if errorlevel 1 (
    echo Python 3 was not found. Install it from python.org, then run this again.
    pause
    exit /b 1
)

%PY% tools\release.py %*
set "RESULT=%ERRORLEVEL%"
echo.
if "%RESULT%"=="0" (
    echo Finished. The console's Releases tab shows the new version.
) else (
    echo Something stopped the release. Read the messages above.
)
pause
exit /b %RESULT%

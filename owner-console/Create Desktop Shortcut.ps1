# Creates a "TextPic Owner" shortcut on the desktop for TextPicOwner.exe.
# Right-click this file -> "Run with PowerShell".

$ErrorActionPreference = "Stop"

$exe = Join-Path $PSScriptRoot "TextPicOwner.exe"
if (-not (Test-Path $exe)) {
    Write-Host "TextPicOwner.exe was not found next to this script." -ForegroundColor Red
    Write-Host "Keep this file inside the owner-console folder from the release."
    Read-Host "Press Enter to close"
    exit 1
}

$desktop = [Environment]::GetFolderPath("Desktop")
$linkPath = Join-Path $desktop "TextPic Owner.lnk"

$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($linkPath)
$shortcut.TargetPath = $exe
$shortcut.WorkingDirectory = $PSScriptRoot
$shortcut.IconLocation = "$exe,0"
$shortcut.Description = "TextPic Studio owner console: licenses, blocks and image plans"
$shortcut.Save()

Write-Host "Shortcut created on the desktop:" -ForegroundColor Green
Write-Host "  $linkPath"
Read-Host "Press Enter to close"

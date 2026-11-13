@ECHO OFF
SETLOCAL
WHERE mvn >NUL 2>NUL
IF %ERRORLEVEL% EQU 0 (
  mvn %*
  EXIT /B %ERRORLEVEL%
)
ECHO Maven wrapper requires mvn in PATH or PowerShell downloader. >&2
EXIT /B 1

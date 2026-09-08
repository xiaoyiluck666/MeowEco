param([string]$BackupPath)
$ErrorActionPreference = 'Stop'
$root = (Get-Location).Path

$files = @(
  'CHANGELOG.md',
  'meoweco-paper/src/main/java/com/xiaoyiluck/meoweco/api/MeowEcoAPIImpl.java',
  'meoweco-paper/src/main/java/com/xiaoyiluck/meoweco/database/AbstractSQLDatabase.java',
  'meoweco-paper/src/main/java/com/xiaoyiluck/meoweco/commands/PayCommand.java',
  'meoweco-core/src/main/java/com/xiaoyiluck/meoweco/database/JdbcDatabaseManager.java',
  'meoweco-core/src/main/java/com/xiaoyiluck/meoweco/service/RichTaxEngine.java',
  'meoweco-core/src/test/java/com/xiaoyiluck/meoweco/service/InMemoryDatabaseManager.java'
)

if ($BackupPath) {
  $resolvedBackup = (Resolve-Path -LiteralPath $BackupPath).Path
  foreach ($file in $files) {
    $source = Join-Path $resolvedBackup $file
    $target = Join-Path $root $file
    if (-not (Test-Path -LiteralPath $source)) { throw "Missing backup file: $source" }
    Copy-Item -LiteralPath $source -Destination $target -Force
  }
  Write-Host "Restored the selected files from $resolvedBackup"
  exit 0
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $root (".codex/rollback-backup-" + $stamp)
New-Item -ItemType Directory -Force -Path $backup | Out-Null
foreach ($file in $files) {
  $source = Join-Path $root $file
  $target = Join-Path $backup $file
  New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
  Copy-Item -LiteralPath $source -Destination $target
}

Write-Host "Backed up current files to $backup"
Write-Host "To roll back this exact state after review, run: .\scripts\rollback-2026-09-07-bugfix.ps1 -BackupPath '$backup'"

# DocuForge AI — system tray control (WinForms via PowerShell)
# Used when DocuForge.Tray.exe is not built; same menu as the WPF app.
# Launch: powershell -WindowStyle Hidden -File DocuForge.Tray.ps1

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

. "$PSScriptRoot\Common.ps1"

$script:Busy = $false
$appName = "DocuForge AI"

function Get-UiUrl {
  try { return Get-DocuForgeAppUrl } catch { return "http://localhost:5174" }
}

function Invoke-HiddenScript {
  param([string]$Name, [string[]]$ScriptArgs = @())
  $path = Join-Path $PSScriptRoot $Name
  $argList = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
    "-File", $path
  ) + $ScriptArgs
  $p = Start-Process -FilePath "powershell.exe" -ArgumentList $argList -Wait -PassThru -WindowStyle Hidden
  return $p.ExitCode
}

$menu = New-Object System.Windows.Forms.ContextMenuStrip
$statusItem = New-Object System.Windows.Forms.ToolStripMenuItem
$statusItem.Text = "Statut : …"
$statusItem.Enabled = $false
[void]$menu.Items.Add($statusItem)
[void]$menu.Items.Add((New-Object System.Windows.Forms.ToolStripSeparator))

$openItem = New-Object System.Windows.Forms.ToolStripMenuItem "Ouvrir DocuForge AI"
$openItem.Add_Click({ Start-Process (Get-UiUrl) })
[void]$menu.Items.Add($openItem)

$startItem = New-Object System.Windows.Forms.ToolStripMenuItem "Démarrer les services"
$startItem.Add_Click({
  if ($script:Busy) { return }
  $script:Busy = $true
  try { [void](Invoke-HiddenScript "Start-Stack.ps1" @("-WaitHealthy")) }
  finally { $script:Busy = $false }
})
[void]$menu.Items.Add($startItem)

$stopItem = New-Object System.Windows.Forms.ToolStripMenuItem "Arrêter les services"
$stopItem.Add_Click({
  if ($script:Busy) { return }
  $script:Busy = $true
  try { [void](Invoke-HiddenScript "Stop-Stack.ps1" @("-Action", "Stop")) }
  finally { $script:Busy = $false }
})
[void]$menu.Items.Add($stopItem)

$restartItem = New-Object System.Windows.Forms.ToolStripMenuItem "Redémarrer les services"
$restartItem.Add_Click({
  if ($script:Busy) { return }
  $script:Busy = $true
  try { [void](Invoke-HiddenScript "Stop-Stack.ps1" @("-Action", "Restart")) }
  finally { $script:Busy = $false }
})
[void]$menu.Items.Add($restartItem)

[void]$menu.Items.Add((New-Object System.Windows.Forms.ToolStripSeparator))

$logsItem = New-Object System.Windows.Forms.ToolStripMenuItem "Voir les logs"
$logsItem.Add_Click({ Start-Process (Get-DocuForgeLogDir) })
[void]$menu.Items.Add($logsItem)

$backupItem = New-Object System.Windows.Forms.ToolStripMenuItem "Sauvegarder la base de données…"
$backupItem.Add_Click({
  $dlg = New-Object System.Windows.Forms.SaveFileDialog
  $dlg.Filter = "SQL (*.sql)|*.sql"
  $dlg.FileName = ("docuforge-backup-{0:yyyyMMdd-HHmmss}.sql" -f (Get-Date))
  if ($dlg.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
    [void](Invoke-HiddenScript "Backup-Database.ps1" @("-OutputFile", $dlg.FileName))
    [System.Windows.Forms.MessageBox]::Show("Sauvegarde terminée.", $appName) | Out-Null
  }
})
[void]$menu.Items.Add($backupItem)

$updItem = New-Object System.Windows.Forms.ToolStripMenuItem "Vérifier les mises à jour"
$updItem.Add_Click({
  $check = & (Join-Path $PSScriptRoot "Update-Stack.ps1") 2>&1 | Out-String
  if ($check -match "update=True" -or $check -match "update=true") {
    $r = [System.Windows.Forms.MessageBox]::Show(
      "Une mise à jour est disponible. L'appliquer maintenant ?",
      $appName,
      [System.Windows.Forms.MessageBoxButtons]::YesNo)
    if ($r -eq [System.Windows.Forms.DialogResult]::Yes) {
      [void](Invoke-HiddenScript "Update-Stack.ps1" @("-Apply"))
    }
  } else {
    [System.Windows.Forms.MessageBox]::Show("DocuForge AI est à jour.", $appName) | Out-Null
  }
})
[void]$menu.Items.Add($updItem)

[void]$menu.Items.Add((New-Object System.Windows.Forms.ToolStripSeparator))

$quitItem = New-Object System.Windows.Forms.ToolStripMenuItem "Quitter"
$quitItem.Add_Click({
  $notify.Visible = $false
  [System.Windows.Forms.Application]::Exit()
})
[void]$menu.Items.Add($quitItem)

$notify = New-Object System.Windows.Forms.NotifyIcon
$notify.Text = $appName
$notify.ContextMenuStrip = $menu
$icoPath = Join-Path (Split-Path $PSScriptRoot) "assets\docuforge.ico"
if (-not (Test-Path $icoPath)) {
  $icoPath = Join-Path ${env:ProgramFiles} "DocuForge AI\docuforge.ico"
}
if (Test-Path $icoPath) {
  $notify.Icon = New-Object System.Drawing.Icon $icoPath
} else {
  $notify.Icon = [System.Drawing.SystemIcons]::Application
}
$notify.Visible = $true
$notify.Add_DoubleClick({ Start-Process (Get-UiUrl) })

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 8000
$timer.Add_Tick({
  try {
    $out = & (Join-Path $PSScriptRoot "Get-Status.ps1") 2>&1 | Out-String
    if ($out -match "EnLigne") { $statusItem.Text = "Statut : en ligne" }
    elseif ($out -match "Demarrage") { $statusItem.Text = "Statut : démarrage…" }
    elseif ($out -match "Erreur") { $statusItem.Text = "Statut : erreur" }
    else { $statusItem.Text = "Statut : hors ligne" }
    $notify.Text = "$appName — $($statusItem.Text)"
  } catch {
    $statusItem.Text = "Statut : erreur"
  }
})
$timer.Start()

[System.Windows.Forms.Application]::Run()

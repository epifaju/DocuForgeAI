using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Windows;
using Forms = System.Windows.Forms;
using Drawing = System.Drawing;

namespace DocuForge.Tray;

internal enum ServiceStatus
{
    Online,
    Offline,
    Starting,
    Error
}

internal sealed class TrayApplication : IDisposable
{
    private readonly Forms.NotifyIcon _icon;
    private readonly Forms.ContextMenuStrip _menu;
    private readonly Forms.ToolStripMenuItem _statusItem;
    private readonly System.Windows.Threading.DispatcherTimer _timer;
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(3) };
    private ServiceStatus _status = ServiceStatus.Offline;
    private string _appUrl = "http://localhost:5174";
    private bool _busy;

    public TrayApplication()
    {
        _menu = new Forms.ContextMenuStrip();
        _statusItem = new Forms.ToolStripMenuItem(Strings.StatusUnknown) { Enabled = false };
        _menu.Items.Add(_statusItem);
        _menu.Items.Add(new Forms.ToolStripSeparator());
        _menu.Items.Add(Strings.OpenApp, null, (_, _) => OpenApp());
        _menu.Items.Add(Strings.Start, null, async (_, _) => await RunScriptAsync("Start-Stack.ps1", "-WaitHealthy"));
        _menu.Items.Add(Strings.Stop, null, async (_, _) => await RunScriptAsync("Stop-Stack.ps1", "-Action Stop"));
        _menu.Items.Add(Strings.Restart, null, async (_, _) => await RunScriptAsync("Stop-Stack.ps1", "-Action Restart"));
        _menu.Items.Add(new Forms.ToolStripSeparator());
        _menu.Items.Add(Strings.ViewLogs, null, (_, _) => OpenLogs());
        _menu.Items.Add(Strings.BackupDb, null, async (_, _) => await BackupAsync());
        _menu.Items.Add(Strings.CheckUpdates, null, async (_, _) => await CheckUpdatesAsync());
        _menu.Items.Add(new Forms.ToolStripSeparator());
        _menu.Items.Add(Strings.Quit, null, (_, _) => Quit());

        _icon = new Forms.NotifyIcon
        {
            Text = Strings.AppName,
            Visible = true,
            ContextMenuStrip = _menu,
            Icon = LoadIcon()
        };
        _icon.DoubleClick += (_, _) => OpenApp();

        _timer = new System.Windows.Threading.DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(8)
        };
        _timer.Tick += async (_, _) => await RefreshStatusAsync();
    }

    public void Start()
    {
        Paths.EnsureDataDirs();
        LoadUrlFromEnv();
        _timer.Start();
        _ = RefreshStatusAsync();
    }

    private static Drawing.Icon LoadIcon()
    {
        var ico = Path.Combine(AppContext.BaseDirectory, "Assets", "docuforge.ico");
        if (File.Exists(ico))
        {
            return new Drawing.Icon(ico);
        }
        return Drawing.SystemIcons.Application;
    }

    private void LoadUrlFromEnv()
    {
        var envPath = Paths.EnvFile;
        if (!File.Exists(envPath)) return;
        foreach (var line in File.ReadAllLines(envPath))
        {
            var t = line.Trim();
            if (t.StartsWith("APP_BASE_URL=", StringComparison.OrdinalIgnoreCase))
            {
                _appUrl = t["APP_BASE_URL=".Length..].Trim();
            }
            else if (t.StartsWith("FRONTEND_PORT=", StringComparison.OrdinalIgnoreCase) &&
                     string.IsNullOrWhiteSpace(_appUrl))
            {
                _appUrl = $"http://localhost:{t["FRONTEND_PORT=".Length..].Trim()}";
            }
        }
    }

    private async Task RefreshStatusAsync()
    {
        if (_busy) return;
        try
        {
            LoadUrlFromEnv();
            var apiPort = ReadEnv("BACKEND_PORT", "18081");
            var apiOk = false;
            var uiOk = false;
            try
            {
                using var api = await _http.GetAsync($"http://127.0.0.1:{apiPort}/actuator/health/readiness");
                apiOk = api.IsSuccessStatusCode;
            }
            catch { /* offline */ }
            try
            {
                using var ui = await _http.GetAsync(_appUrl);
                uiOk = ui.IsSuccessStatusCode;
            }
            catch { /* offline */ }

            _status = apiOk ? ServiceStatus.Online
                : uiOk ? ServiceStatus.Starting
                : ServiceStatus.Offline;

            _statusItem.Text = _status switch
            {
                ServiceStatus.Online => Strings.StatusOnline,
                ServiceStatus.Starting => Strings.StatusStarting,
                ServiceStatus.Error => Strings.StatusError,
                _ => Strings.StatusOffline
            };
            _icon.Text = $"{Strings.AppName} — {_statusItem.Text}";
        }
        catch
        {
            _status = ServiceStatus.Error;
            _statusItem.Text = Strings.StatusError;
        }
    }

    private async Task RunScriptAsync(string scriptName, string args)
    {
        if (_busy)
        {
            Forms.MessageBox.Show(Strings.Busy, Strings.AppName);
            return;
        }
        _busy = true;
        try
        {
            _statusItem.Text = Strings.StatusStarting;
            var code = await ScriptHost.RunAsync(scriptName, args);
            if (code != 0)
            {
                Forms.MessageBox.Show(Strings.ActionFailed, Strings.AppName, Forms.MessageBoxButtons.OK, Forms.MessageBoxIcon.Warning);
            }
        }
        finally
        {
            _busy = false;
            await RefreshStatusAsync();
        }
    }

    private void OpenApp()
    {
        LoadUrlFromEnv();
        try
        {
            Process.Start(new ProcessStartInfo { FileName = _appUrl, UseShellExecute = true });
        }
        catch (Exception ex)
        {
            Forms.MessageBox.Show(ex.Message, Strings.AppName);
        }
    }

    private void OpenLogs()
    {
        var dir = Paths.LogDir;
        Directory.CreateDirectory(dir);
        Process.Start(new ProcessStartInfo { FileName = dir, UseShellExecute = true });
    }

    private async Task BackupAsync()
    {
        using var dlg = new Forms.SaveFileDialog
        {
            Filter = "SQL (*.sql)|*.sql",
            FileName = $"docuforge-backup-{DateTime.Now:yyyyMMdd-HHmmss}.sql"
        };
        if (dlg.ShowDialog() != Forms.DialogResult.OK) return;
        var code = await ScriptHost.RunAsync("Backup-Database.ps1", $"-OutputFile \"{dlg.FileName}\"");
        Forms.MessageBox.Show(code == 0 ? Strings.BackupDone : Strings.ActionFailed, Strings.AppName);
        await RefreshStatusAsync();
    }

    private async Task CheckUpdatesAsync()
    {
        if (_busy) return;
        _busy = true;
        try
        {
            var check = await ScriptHost.RunCaptureAsync("Update-Stack.ps1", "");
            if (check.Output.Contains("update=True", StringComparison.OrdinalIgnoreCase) ||
                check.Output.Contains("update=true", StringComparison.OrdinalIgnoreCase))
            {
                var r = Forms.MessageBox.Show(Strings.UpdateAvailable, Strings.AppName,
                    Forms.MessageBoxButtons.YesNo, Forms.MessageBoxIcon.Question);
                if (r == Forms.DialogResult.Yes)
                {
                    var apply = await ScriptHost.RunAsync("Update-Stack.ps1", "-Apply");
                    Forms.MessageBox.Show(apply == 0 ? Strings.UpdateDone : Strings.ActionFailed, Strings.AppName);
                }
            }
            else
            {
                Forms.MessageBox.Show(Strings.UpToDate, Strings.AppName);
            }
        }
        finally
        {
            _busy = false;
            await RefreshStatusAsync();
        }
    }

    private void Quit()
    {
        // Leave containers running unless user stopped them explicitly
        Dispose();
        System.Windows.Application.Current.Shutdown();
    }

    private static string ReadEnv(string key, string fallback)
    {
        if (!File.Exists(Paths.EnvFile)) return fallback;
        foreach (var line in File.ReadAllLines(Paths.EnvFile))
        {
            var t = line.Trim();
            if (t.StartsWith(key + "=", StringComparison.OrdinalIgnoreCase))
                return t[(key.Length + 1)..].Trim();
        }
        return fallback;
    }

    public void Dispose()
    {
        _timer.Stop();
        _icon.Visible = false;
        _icon.Dispose();
        _menu.Dispose();
        _http.Dispose();
    }
}

internal static class Paths
{
    public static string DataRoot =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "DocuForgeAI");

    public static string EnvFile => Path.Combine(DataRoot, ".env");
    public static string LogDir => Path.Combine(DataRoot, "logs");
    public static string ScriptsDir
    {
        get
        {
            var install = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "DocuForge AI", "scripts");
            if (Directory.Exists(install)) return install;
            // Dev: installer/scripts relative to tray project output
            var dev = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "scripts"));
            if (Directory.Exists(dev)) return dev;
            return install;
        }
    }

    public static void EnsureDataDirs()
    {
        Directory.CreateDirectory(DataRoot);
        Directory.CreateDirectory(LogDir);
    }
}

internal static class ScriptHost
{
    public static Task<int> RunAsync(string script, string args) =>
        Task.Run(() => Run(script, args).ExitCode);

    public static Task<(int ExitCode, string Output)> RunCaptureAsync(string script, string args) =>
        Task.Run(() => Run(script, args));

    private static (int ExitCode, string Output) Run(string script, string args)
    {
        var path = Path.Combine(Paths.ScriptsDir, script);
        if (!File.Exists(path))
        {
            return (1, "SCRIPT_MISSING");
        }

        var psi = new ProcessStartInfo
        {
            FileName = "powershell.exe",
            Arguments = $"-NoProfile -ExecutionPolicy Bypass -File \"{path}\" {args}",
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            WindowStyle = ProcessWindowStyle.Hidden
        };
        using var p = Process.Start(psi)!;
        var stdout = p.StandardOutput.ReadToEnd();
        var stderr = p.StandardError.ReadToEnd();
        p.WaitForExit();
        var combined = stdout + stderr;
        try
        {
            File.AppendAllText(
                Path.Combine(Paths.LogDir, $"tray-{DateTime.Now:yyyyMMdd}.log"),
                $"{DateTime.Now:O} {script} => {p.ExitCode}\n{Redact(combined)}\n");
        }
        catch { /* ignore log errors */ }
        return (p.ExitCode, combined);
    }

    private static string Redact(string text)
    {
        if (string.IsNullOrEmpty(text)) return text;
        return System.Text.RegularExpressions.Regex.Replace(
            text,
            @"(?i)(PASSWORD|SECRET|TOKEN)\s*[=:]\s*\S+",
            "$1=***");
    }
}

internal static class Strings
{
    // FR default — swap resource culture for PT later
    public static string AppName => "DocuForge AI";
    public static string StatusUnknown => "Statut : …";
    public static string StatusOnline => "Statut : en ligne";
    public static string StatusOffline => "Statut : hors ligne";
    public static string StatusStarting => "Statut : démarrage…";
    public static string StatusError => "Statut : erreur";
    public static string OpenApp => "Ouvrir DocuForge AI";
    public static string Start => "Démarrer les services";
    public static string Stop => "Arrêter les services";
    public static string Restart => "Redémarrer les services";
    public static string ViewLogs => "Voir les logs";
    public static string BackupDb => "Sauvegarder la base de données…";
    public static string CheckUpdates => "Vérifier les mises à jour";
    public static string Quit => "Quitter";
    public static string Busy => "Une opération est déjà en cours.";
    public static string ActionFailed => "L'opération a échoué. Consultez les logs via « Voir les logs ».";
    public static string BackupDone => "Sauvegarde terminée.";
    public static string UpdateAvailable => "Une mise à jour est disponible. L'appliquer maintenant ?";
    public static string UpdateDone => "Mise à jour terminée.";
    public static string UpToDate => "DocuForge AI est à jour.";
}

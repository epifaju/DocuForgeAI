using System.Globalization;

namespace DocuForge.Tray;

public partial class App : System.Windows.Application
{
    protected override void OnStartup(System.Windows.StartupEventArgs e)
    {
        base.OnStartup(e);
        // FR by default; structure ready for PT (CultureInfo)
        var culture = new CultureInfo("fr-FR");
        CultureInfo.DefaultThreadCurrentCulture = culture;
        CultureInfo.DefaultThreadCurrentUICulture = culture;

        var mgr = new TrayApplication();
        mgr.Start();
    }
}

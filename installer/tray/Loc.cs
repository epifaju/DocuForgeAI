using System.Globalization;
using System.Resources;

namespace DocuForge.Tray;

/// <summary>
/// Thin i18n façade — FR embedded in <see cref="Strings"/>; PT via satellite later.
/// </summary>
internal static class Loc
{
    private static readonly ResourceManager? Pt;

    static Loc()
    {
        try
        {
            Pt = new ResourceManager("DocuForge.Tray.Resources.Strings", typeof(Loc).Assembly);
        }
        catch
        {
            Pt = null;
        }
    }

    public static string Get(string frDefault, string key)
    {
        if (CultureInfo.CurrentUICulture.TwoLetterISOLanguageName == "pt" && Pt is not null)
        {
            var v = Pt.GetString(key);
            if (!string.IsNullOrEmpty(v)) return v;
        }
        return frDefault;
    }
}

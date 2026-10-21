// VAJRA OVERDRIVE — setup wizard. Compiled at build time by the in-box C# compiler
// (C:\Windows\Microsoft.NET\Framework*\v4.0.30319\csc.exe), so it runs on every
// Windows without installing anything. C# 5 and .NET 4 only, on purpose.
//
// Pages: welcome -> terms -> folder -> options -> install -> finish. The game itself
// rides inside the exe (the embedded "payload" resource — the zip gradle's payloadZip
// built) and lands in the folder the pilot picks. Shortcuts on the desktop and in the
// start menu, both wearing the mk1 ship. --silent dir [relay] does the same without UI.
using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Windows.Forms;

namespace vajra.setup {
static class SetupWizard {
    static string dest = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "Programs", "Vajra Overdrive");
    static string relay = "";
    static bool lnkDesktop = true, lnkMenu = true;

    const string Title = "Vajra Overdrive Setup";
    const string TermsText =
        "VAJRA OVERDRIVE — TERMS OF PLAY\r\n\r\n" +
        "1. This game is given as-is, for personal play. Fly it, share it, never sell it.\r\n\r\n" +
        "2. It never phones home. Everything it knows stays on your machine. The only network\r\n" +
        "   traffic is the game's own: multiplayer packets and voice inside one co-op room.\r\n\r\n" +
        "3. Online play opens a door on your OWN router (UPnP) so a friend can knock\r\n" +
        "   directly. Nothing is hosted anywhere else and no third-party service is used.\r\n" +
        "   A shared relay box is optional — only when a router refuses every mapping —\r\n" +
        "   and then packets and voice flow through that relay, so pick an owner you trust.\r\n\r\n" +
        "4. A microphone is only used while you hold V to talk. Push-to-talk on purpose.\r\n\r\n" +
        "5. Java 17 is required. Some copies carry their own runtime; when one does not,\r\n" +
        "   the setup says where the free copy lives (adoptium.net).\r\n\r\n" +
        "6. No warranty of any kind. If an enemy sphere eats your plane, that is the game\r\n" +
        "   working as designed.\r\n\r\n" +
        "Installing into a cabinet, arcade or school lab is fine — fly with everyone.";

    [STAThread]
    static void Main(string[] args) {
        if (args.Length > 0 && args[0] == "--version") { // the in-game updater probes the stamp
            Console.WriteLine(EmbeddedVersion());
            return;
        }
        if (args.Length > 0 && args[0] == "--silent") {
            if (args.Length > 1) dest = args[1];
            if (args.Length > 2 && args[2] != "-") relay = args[2]; // "-" keeps relay.txt untouched
            string fail = Deliver(delegate(string s) { Console.WriteLine(s); });
            if (fail == null && args.Length > 3 && args[3] == "launch") Launch();
            Environment.Exit(fail == null ? 0 : 3);
        }
        Application.EnableVisualStyles();
        Application.Run(new Wizard());
    }

    // ---- the actual install, shared by the wizard and --silent --------------------------

    static string Deliver(Action<string> step) {
        step("Preparing the files ...");
        string stage = Path.Combine(Path.GetTempPath(), "VajraOverdriveSetup");
        if (Directory.Exists(stage)) Directory.Delete(stage, true); // last run's leftovers crash the extract
        Directory.CreateDirectory(stage);
        string app = Path.Combine(stage, "app");
        string payload = Path.Combine(stage, "payload.zip");
        using (var s = Assembly.GetExecutingAssembly().GetManifestResourceStream("payload"))
        using (var f = File.Create(payload)) s.CopyTo(f);
        if (Directory.Exists(app)) Directory.Delete(app, true);
        ZipFile.ExtractToDirectory(payload, stage);
        if (!Directory.Exists(app)) return "the game files were missing from the setup";

        // the payload may carry its own runtime: with it, the machine needs nothing
        if (!File.Exists(Path.Combine(stage, "app", "jre", "bin", "java.exe"))) {
            step("Looking for Java 17+ ...");
            string jv = CheckJava();
            if (jv != null) return jv;
        }
        step("Installing into " + dest + " ...");
        if (CloseRunning(dest) > 0) step("Closed the running game and its relay ...");
        CopyDir(app, dest);
        string relayDir = Path.Combine(stage, "relay");   // the friend-hosted relay box rides along
        if (Directory.Exists(relayDir)) CopyDir(relayDir, Path.Combine(dest, "relay"));
        foreach (var name in new[] { "run-relay.bat", "host-server.bat", "host-server.ps1" })
            if (File.Exists(Path.Combine(stage, name)))
                File.Copy(Path.Combine(stage, name), Path.Combine(dest, name), true);
        if (relay.Trim().Length > 0)
            File.WriteAllText(Path.Combine(dest, "relay.txt"), relay.Trim());

        step("Shortcuts ...");
        if (lnkDesktop) MakeShortcut(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory));
        if (lnkMenu) MakeShortcut(Environment.GetFolderPath(Environment.SpecialFolder.Programs));
        step("Done.");
        return null;
    }

    static string CheckJava() {
        try {
            var psi = new ProcessStartInfo("java", "-version") {
                UseShellExecute = false, CreateNoWindow = true,
                RedirectStandardError = true, RedirectStandardOutput = true
            };
            var p = Process.Start(psi);
            string outp = p.StandardError.ReadToEnd() + p.StandardOutput.ReadToEnd();
            p.WaitForExit(20000);
            int at = outp.IndexOf("version \"");
            int major = 0;
            if (at >= 0) {
                string digits = outp.Substring(at + 9);
                int stop = digits.IndexOfAny(new[] { '.', '"' });
                if (stop < 0) stop = digits.Length;
                int.TryParse(digits.Substring(0, stop), out major);
            }
            if (major >= 17) return null;
            if (major > 0) return "Java " + major + " was found, but the game needs 17 or newer.\nInstall the free Temurin 17 from https://adoptium.net , then run this setup again.";
            return "Java was not found on this machine.\nInstall the free Temurin 17 from https://adoptium.net , then run this setup again.";
        } catch (Exception) {
            return "Java was not found on this machine.\nInstall the free Temurin 17 from https://adoptium.net , then run this setup again.";
        }
    }

    static void CopyDir(string src, string dst) {
        Directory.CreateDirectory(dst);
        foreach (var f in Directory.GetFiles(src))
            File.Copy(f, Path.Combine(dst, Path.GetFileName(f)), true);
        foreach (var d in Directory.GetDirectories(src))
            CopyDir(d, Path.Combine(dst, Path.GetFileName(d)));
    }

    // the game and its auto-relay hold their jars open, so an upgrade into a live
    // install fails file by file - stop whatever belongs under that install first
    static int CloseRunning(string under) {
        int killed = 0;
        int mine = Process.GetCurrentProcess().Id;
        foreach (var p in Process.GetProcesses()) {
            if (p.Id == mine) continue; // the wizard may itself live under the install
            string mod = null;
            try { mod = p.MainModule == null ? null : p.MainModule.FileName; } catch { }
            if (mod == null || !mod.StartsWith(under, StringComparison.OrdinalIgnoreCase)) continue;
            try { p.Kill(); p.WaitForExit(4000); killed++; } catch { }
        }
        return killed;
    }

    // the payload's build stamp, for the in-game updater: it compares this with the
    // gamever.txt the last install carried and skips a pointless same-build reinstall
    static string EmbeddedVersion() {
        string stage = Path.Combine(Path.GetTempPath(),
            "VajraOverdriveProbe" + Process.GetCurrentProcess().Id);
        if (Directory.Exists(stage)) Directory.Delete(stage, true);
        Directory.CreateDirectory(stage);
        string payload = Path.Combine(stage, "payload.zip");
        using (var s = Assembly.GetExecutingAssembly().GetManifestResourceStream("payload"))
        using (var f = File.Create(payload)) s.CopyTo(f);
        ZipFile.ExtractToDirectory(payload, stage);
        var vf = Path.Combine(stage, "app", "gamever.txt");
        string v = File.Exists(vf) ? File.ReadAllText(vf).Trim() : "";
        try { Directory.Delete(stage, true); } catch { }
        return v;
    }

    // the bundled runtime runs the game with no PATH java at all, so the launcher
    // preference matters: play.bat (jre + javaw) first, the old script as fallback
    static void Launch() {
        string bat = File.Exists(Path.Combine(dest, "play.bat")) ? "play.bat"
            : Path.Combine("bin", "lwjgl3.bat");
        try {
            Process.Start(new ProcessStartInfo(Path.Combine(dest, bat))
                { WorkingDirectory = dest, UseShellExecute = true });
        } catch (Exception) { /* the finish page already said where it lives */ }
    }

    // a .lnk through the shell COM — the same kind of shortcut the old install.bat made
    static void MakeShortcut(string folder) {
        // play.bat when the copy came with it (it prefers the bundled Java runtime);
        // otherwise the Gradle start script beside bin\
        string launcher = File.Exists(Path.Combine(dest, "play.bat"))
            ? Path.Combine(dest, "play.bat")
            : Path.Combine(dest, "bin", "lwjgl3.bat");
        var t = Type.GetTypeFromProgID("WScript.Shell");
        var shell = Activator.CreateInstance(t);
        var lnk = t.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell,
            new object[] { Path.Combine(folder, "Vajra Overdrive.lnk") });
        var lk = lnk.GetType();
        lk.InvokeMember("TargetPath", BindingFlags.SetProperty, null, lnk,
            new object[] { launcher });
        lk.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, lnk,
            new object[] { dest });
        lk.InvokeMember("IconLocation", BindingFlags.SetProperty, null, lnk,
            new object[] { Path.Combine(dest, "vajra.ico") + ", 0" });
        lk.InvokeMember("Save", BindingFlags.InvokeMethod, null, lnk, new object[0]);
    }

    // ---- the wizard ----------------------------------------------------------------------

    class Wizard : Form {
        readonly Panel[] pages;
        FlowLayoutPanel nav;
        Button back, next;
        CheckBox acceptBox, desktopBox, menuBox, launchBox;
        TextBox destBox, relayBox, log;
        int at = 0;

        public Wizard() {
            Text = Title;
            Icon = LoadIcon();
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = MinimizeBox = false;
            ClientSize = new Size(560, 380);
            StartPosition = FormStartPosition.CenterScreen;

            var ship = new PictureBox { Image = LoadShip(),
                SizeMode = PictureBoxSizeMode.Zoom, Size = new Size(64, 64), Location = new Point(10, 6) };
            Controls.Add(ship);
            var head = new Label { Text = "VAJRA OVERDRIVE", Font = new Font("Georgia", 17, GraphicsUnit.Point),
                ForeColor = Color.FromArgb(212, 168, 62), Location = new Point(86, 20), AutoSize = true };
            Controls.Add(head);

            pages = new Panel[] { Welcome(), Terms(), ThePath(), Options(), Installing(), Finish() };
            var body = new Panel { Location = new Point(12, 60), Size = new Size(536, 264),
                BorderStyle = BorderStyle.FixedSingle, BackColor = Color.FromArgb(248, 246, 240) };
            foreach (var p in pages) { p.Size = body.ClientSize; p.BackColor = body.BackColor; body.Controls.Add(p); p.Visible = false; }
            Controls.Add(body);

            nav = new FlowLayoutPanel { FlowDirection = FlowDirection.RightToLeft,
                Location = new Point(200, 334), Size = new Size(348, 40), Anchor = AnchorStyles.Right };
            next = new Button { Text = "Next >" };
            next.MinimumSize = new Size(96, 28);
            next.Click += delegate { Advance(1); };
            back = new Button { Text = "< Back" };
            back.MinimumSize = new Size(96, 28);
            back.Click += delegate { Advance(-1); };
            var cancel = new Button { Text = "Cancel" };
            cancel.Click += delegate { Close(); };
            nav.Controls.Add(next); nav.Controls.Add(back); nav.Controls.Add(cancel);
            Controls.Add(nav);
            AcceptButton = next;

            ShowPage(0);
        }

        // ---- pages -----------------------------------------------------------------------

        Panel Welcome() {
            var p = NewPanel();
            var t = new Label { Text =
                "Welcome to the pilot's setup.\r\n\r\n" +
                "This wizard installs Vajra Overdrive — the co-op bullet-flyer —\r\n" +
                "onto this machine: the game, its launcher and shortcuts.\r\n\r\n" +
                "Four quick questions ahead:\r\n" +
                "  · accept the terms of play\r\n" +
                "  · pick the install folder (no admin rights needed)\r\n" +
                "  · choose the shortcuts you want\r\n" +
                "  · a shared relay box — only if you want one\r\n\r\n" +
                "Java 17 or newer is required (free copy at adoptium.net); the setup checks.",
                AutoSize = true, Location = new Point(12, 10) };
            p.Controls.Add(t);
            return p;
        }

        Panel Terms() {
            var p = NewPanel();
            var box = new TextBox { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical,
                Location = new Point(10, 10), Size = new Size(512, 198), BackColor = Color.White };
            box.Text = TermsText;
            p.Controls.Add(box);
            acceptBox = new CheckBox { Text = "I have read and accept the terms of play",
                AutoSize = true, Location = new Point(10, 220) };
            acceptBox.CheckedChanged += delegate { next.Enabled = acceptBox.Checked; };
            p.Controls.Add(acceptBox);
            return p;
        }

        Panel ThePath() {
            var p = NewPanel();
            var t = new Label { Text =
                "Where should the game fly from?\r\n\r\n" +
                "Install into your own profile by default — installing requires no admin rights.\r\n" +
                "The folder holds the game, its launcher and the vajra.txt note on flying\r\n" +
                "with a friend.",
                AutoSize = true, Location = new Point(10, 10) };
            p.Controls.Add(t);
            destBox = new TextBox { Text = dest, Width = 420, Location = new Point(10, 96) };
            p.Controls.Add(destBox);
            var browse = new Button { Text = "Browse ...", Width = 88, Location = new Point(436, 94) };
            browse.Click += delegate {
                var d = new FolderBrowserDialog { SelectedPath = destBox.Text, Description = "Install Vajra Overdrive into:" };
                if (d.ShowDialog(this) == DialogResult.OK) destBox.Text = d.SelectedPath;
            };
            p.Controls.Add(browse);
            return p;
        }

        Panel Options() {
            var p = NewPanel();
            desktopBox = new CheckBox { Text = "Create a shortcut on the desktop", Checked = true,
                AutoSize = true, Location = new Point(10, 18) };
            menuBox = new CheckBox { Text = "Create a shortcut in the start menu", Checked = true,
                AutoSize = true, Location = new Point(10, 46) };
            p.Controls.Add(desktopBox); p.Controls.Add(menuBox);
            var t = new Label { Text =
                "\r\nA SHARED relay box (yourbox:27040) — optional:\r\n" +
                "The game opens its own door on your router for internet play, so most\r\n" +
                "pilots leave this empty. Fill it in only when you and your friends point\r\n" +
                "at the same relay box.",
                AutoSize = true, Location = new Point(10, 84) };
            p.Controls.Add(t);
            relayBox = new TextBox { Width = 420, Location = new Point(10, 148) };
            p.Controls.Add(relayBox);
            var note = new Label { Text =
                "Leave it empty unless you run a shared relay box. You can add or change\r\n" +
                "relay.txt beside the game later.",
                AutoSize = true, Font = new Font("Microsoft Sans Serif", 8, FontStyle.Italic),
                ForeColor = Color.FromArgb(96, 96, 96), Location = new Point(10, 186) };
            p.Controls.Add(note);
            return p;
        }

        Panel Installing() {
            var p = NewPanel();
            var t = new Label { Text = "Installing ...", AutoSize = true, Location = new Point(10, 10),
                Font = new Font("Microsoft Sans Serif", 10, FontStyle.Bold) };
            p.Controls.Add(t);
            log = new TextBox { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical,
                Location = new Point(10, 38), Size = new Size(512, 200), BackColor = Color.White,
                Font = new Font("Consolas", 9) };
            p.Controls.Add(log);
            return p;
        }

        Panel Finish() {
            var p = NewPanel();
            var t = new Label { Text = "Installed and ready to fly.", AutoSize = true,
                Location = new Point(10, 14), Font = new Font("Microsoft Sans Serif", 11, FontStyle.Bold) };
            p.Controls.Add(t);
            launchBox = new CheckBox { Text = "Launch Vajra Overdrive now", Checked = true,
                AutoSize = true, Location = new Point(10, 56) };
            p.Controls.Add(launchBox);
            var tip = new Label { Text =
                "\r\nLAN play just works: MULTIPLAYER -> HOST A RUN, and your friend's\r\n" +
                "list shows your room — no address to type.\r\n" +
                "Internet play opens its own door on your router; the one code the\r\n" +
                "lobby shows is all a friend needs. vajra.txt says more.",
                AutoSize = true, ForeColor = Color.FromArgb(96, 96, 96), Location = new Point(10, 100) };
            p.Controls.Add(tip);
            return p;
        }

        // ---- plumbing ----------------------------------------------------------------------

        static Panel NewPanel() { return new Panel(); }

        static Icon LoadIcon() {
            try {
                using (var s = Assembly.GetExecutingAssembly().GetManifestResourceStream("vajra"))
                    return new Icon(s);
            } catch (Exception) { return null; } // the title bar still shows without it
        }

        static Image LoadShip() {
            using (var s = Assembly.GetExecutingAssembly().GetManifestResourceStream("ship"))
                return Image.FromStream(s);
        }

        void ShowPage(int i) {
            pages[at].Visible = false;
            pages[i].Visible = true;
            at = i;
            Text = Title + "  -  step " + (i + 1) + " of " + pages.Length;
            back.Enabled = i > 0 && i < pages.Length - 1;
            next.Enabled = (i == 1) ? acceptBox.Checked : (i != 4);
            next.Text = (i == 3) ? "Install" : (i == pages.Length - 1 ? "Finish" : "Next >");
            if (i == 4) { // entering the install page: run it all
                dest = destBox.Text.Trim();
                relay = relayBox.Text;
                lnkDesktop = desktopBox.Checked;
                lnkMenu = menuBox.Checked;
                BeginInstall();
            }
        }

        void Advance(int dir) {
            if (at == pages.Length - 1 && dir > 0) {
                if (launchBox.Checked) Launch();
                Close();
                return;
            }
            ShowPage(Math.Max(0, Math.Min(pages.Length - 1, at + dir)));
        }

        void BeginInstall() {
            log.Text = "";
            nav.Visible = false; // nobody backs out mid-copy
            Application.DoEvents();
            string fail = Deliver(delegate(string s) {
                log.AppendText(s + "\r\n");
                log.Update();
                Application.DoEvents();
            });
            if (fail != null) {
                MessageBox.Show(fail, Title, MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
                nav.Visible = true;
                ShowPage(3); // back to the options page to try again
                return;
            }
            ShowPage(5);
            nav.Visible = true;
        }
    }
}
}

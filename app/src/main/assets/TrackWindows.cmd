@echo off
setlocal
set "CSC=%SystemRoot%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist "%CSC%" set "CSC=%SystemRoot%\Microsoft.NET\Framework\v4.0.30319\csc.exe"
set "SRC=%~dp0TrackWindows.cs"
set "OUT=%~dp0TrackWindows.exe"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p='%~f0'; $lines=Get-Content -LiteralPath $p -Encoding UTF8; $cut=-1; for($i=0; $i -lt $lines.Length; $i++){ if($lines[$i] -eq '---SOURCE---'){ $cut=$i } }; if($cut -lt 0){ exit 1 }; $lines[($cut+1)..($lines.Length-1)] | Set-Content -LiteralPath '%SRC%' -Encoding UTF8"
"%CSC%" /nologo /target:winexe /optimize+ /codepage:65001 /out:"%OUT%" /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.Web.Extensions.dll "%SRC%"
if errorlevel 1 (
  echo TrackWindows could not be built.
  pause
  exit /b 1
)
start "" "%OUT%"
exit /b 0
---SOURCE---
using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Net;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace TrackWindows
{
    internal enum DockMode { None, Scan, Home, Files, Themes, Settings, Quit }

    internal sealed class DockForm : Form
    {
        const int RailOpen = 108;
        const int RailShut = 36;
        const int SheetW = 312;

        readonly Panel sheet;
        readonly Panel scanPage;
        readonly Panel homePage;
        readonly Panel filesPage;
        readonly Panel themePage;
        readonly Panel settingsPage;
        readonly Panel quitPage;
        readonly TextBox urlBox;
        readonly TextBox hostBox;
        readonly TextBox codeBox;
        readonly ListBox accountList;
        readonly ListBox fileList;
        readonly Label connLabel;
        readonly Label homeTitle;
        readonly Label activityLine;
        readonly CheckBox reflectBox;
        readonly List<Label> notes = new List<Label>();
        readonly List<Button> phoneThemeButtons = new List<Button>();
        readonly List<string> accountIds = new List<string>();
        readonly List<string> fileIds = new List<string>();
        readonly List<string> fileNames = new List<string>();
        readonly JavaScriptSerializer json = new JavaScriptSerializer();
        readonly System.Windows.Forms.Timer motion = new System.Windows.Forms.Timer();
        readonly System.Windows.Forms.Timer poll = new System.Windows.Forms.Timer();
        readonly Dictionary<Panel, int> laid = new Dictionary<Panel, int>();
        readonly string[] themeIds = new string[] { "CORAL", "EMERALD", "ELECTRIC_BLUE", "AMBER", "VIOLET", "CYAN" };
        readonly string[] themeLabels = new string[] { "Coral", "Emerald", "Blue", "Amber", "Violet", "Cyan" };

        int rail = RailShut;
        int busy;
        bool placing;
        bool dark = true;
        bool reflect = true;
        bool compact;
        string edge = "right";
        Button leftBtn;
        Button rightBtn;
        Button topBtn;
        Button minBtn;
        Button maxBtn;
        bool connected;
        string token;
        string hostBase = "";
        string accountSig = "";
        string fileSig = "";
        string phoneTheme = "CORAL";
        DockMode mode = DockMode.None;

        DockForm()
        {
            Text = "TrackWindows";
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.Manual;
            ShowInTaskbar = true;
            TopMost = true;
            DoubleBuffered = true;
            KeyPreview = true;
            Font = new Font("Segoe UI", 10f);
            BackColor = Color.FromArgb(214, 224, 248);

            sheet = new Panel();
            sheet.Visible = false;
            Controls.Add(sheet);

            scanPage = Page();
            homePage = Page();
            filesPage = Page();
            themePage = Page();
            settingsPage = Page();
            quitPage = Page();

            urlBox = Box();
            hostBox = Box();
            codeBox = Box();
            codeBox.MaxLength = 4;
            codeBox.CharacterCasing = CharacterCasing.Upper;
            accountList = List();
            fileList = List();
            connLabel = NoteLabel("Not connected");
            homeTitle = Title("Your platforms");
            activityLine = NoteLabel("Activity and cost show here when the phone is connected.");
            reflectBox = new CheckBox();
            reflectBox.Text = "Reflect background";
            reflectBox.Height = 28;
            reflectBox.Checked = true;

            BuildScan();
            BuildHome();
            BuildFiles();
            BuildThemes();
            BuildSettings();
            BuildQuit();

            LoadSettings();
            reflectBox.Checked = reflect;
            ApplyChrome();

            reflectBox.CheckedChanged += delegate
            {
                reflect = reflectBox.Checked;
                SaveSettings();
                Invalidate();
            };
            urlBox.KeyDown += delegate(object sender, KeyEventArgs e)
            {
                if (e.KeyCode == Keys.Enter)
                {
                    e.SuppressKeyPress = true;
                    AddLink(sender, e);
                }
            };
            accountList.DoubleClick += OpenAccount;
            fileList.DoubleClick += OpenFile;

            motion.Interval = 16;
            motion.Tick += Step;
            motion.Start();
            poll.Interval = 400;
            poll.Tick += Poll;
            poll.Start();
            Shown += delegate { MaybeConnect(); };
            FormClosed += delegate { motion.Stop(); poll.Stop(); };
        }

        Panel Page()
        {
            Panel page = new Panel();
            page.Dock = DockStyle.Fill;
            page.Visible = false;
            sheet.Controls.Add(page);
            return page;
        }

        Label Title(string text)
        {
            Label label = new Label();
            label.Text = text;
            label.Font = new Font("Segoe UI", 16f, FontStyle.Bold);
            label.Height = 34;
            label.AutoSize = false;
            return label;
        }

        Label Hint(string text)
        {
            Label label = new Label();
            label.Text = text;
            label.Height = 48;
            label.AutoSize = false;
            return label;
        }

        Label NoteLabel(string text)
        {
            Label label = new Label();
            label.Text = text;
            label.Height = 44;
            label.AutoSize = false;
            notes.Add(label);
            return label;
        }

        TextBox Box()
        {
            TextBox box = new TextBox();
            box.BorderStyle = BorderStyle.FixedSingle;
            box.Font = new Font("Segoe UI", 11f);
            box.Height = 30;
            return box;
        }

        ListBox List()
        {
            ListBox list = new ListBox();
            list.BorderStyle = BorderStyle.None;
            list.IntegralHeight = false;
            list.Height = 250;
            list.Font = new Font("Segoe UI", 10f);
            return list;
        }

        Button Btn(string text)
        {
            Button button = new Button();
            button.Text = text;
            button.Height = 36;
            button.FlatStyle = FlatStyle.Flat;
            button.FlatAppearance.BorderSize = 0;
            button.Cursor = Cursors.Hand;
            button.Font = new Font("Segoe UI", 10f, FontStyle.Bold);
            return button;
        }

        void Stack(Panel page, params Control[] items)
        {
            if (page.Width < 80) return;
            int previous;
            if (laid.TryGetValue(page, out previous) && previous == page.Width && page.Controls.Count > 0) return;
            laid[page] = page.Width;
            bool fresh = page.Controls.Count == 0;
            int y = 4;
            int w = Math.Max(40, page.Width - 8);
            for (int i = 0; i < items.Length; i++)
            {
                Control item = items[i];
                item.SetBounds(4, y, w, item.Height);
                if (fresh) page.Controls.Add(item);
                y += item.Height + 8;
            }
            if (fresh) ApplyChrome();
        }

        void BuildScan()
        {
            Button add = Btn("Add on phone");
            add.Click += AddLink;
            Label status = NoteLabel("");
            Label title = Title("Scan a tag");
            Label hint = Hint("Paste a profile link. The phone saves it. The same link cannot be added twice.");
            scanPage.Resize += delegate { Stack(scanPage, title, hint, urlBox, add, status); };
        }

        void BuildHome()
        {
            Button back = Btn("Back on phone");
            back.Click += delegate { Act("home", ""); };
            Label hint = Hint("Each scanned link is its own color on the phone, even two accounts on the same site. Double-click one to open it there.");
            homePage.Resize += delegate { Stack(homePage, homeTitle, hint, activityLine, accountList, back); };
        }

        void BuildFiles()
        {
            Label title = Title("Files");
            Label hint = Hint("Documents saved on the phone. A folder name is shown when the file is filed. Double-click to open it.");
            filesPage.Resize += delegate { Stack(filesPage, title, hint, fileList); };
        }

        void BuildThemes()
        {
            Button light = Btn("Light glass");
            Button night = Btn("Dark");
            light.Click += delegate { dark = false; SaveSettings(); ApplyChrome(); Invalidate(); };
            night.Click += delegate { dark = true; SaveSettings(); ApplyChrome(); Invalidate(); };
            Button phoneLight = Btn("Phone light");
            Button phoneDark = Btn("Phone dark");
            Button phoneSystem = Btn("Phone system");
            phoneLight.Click += delegate { Act("mode", "LIGHT"); };
            phoneDark.Click += delegate { Act("mode", "DARK"); };
            phoneSystem.Click += delegate { Act("mode", "SYSTEM"); };
            Control[] items = new Control[7 + themeIds.Length];
            items[2] = light;
            items[3] = night;
            items[4] = phoneLight;
            items[5] = phoneDark;
            items[6] = phoneSystem;
            for (int i = 0; i < themeIds.Length; i++)
            {
                Button button = Btn(themeLabels[i]);
                string id = themeIds[i];
                button.Tag = id;
                button.Click += delegate { Act("theme", id); };
                phoneThemeButtons.Add(button);
                items[7 + i] = button;
            }
            Label title = Title("Appearance");
            Label hint = Hint("Light and dark change this window. Phone light, dark, and system change the app on the phone. The colors below are the phone accent.");
            items[0] = title;
            items[1] = hint;
            themePage.Resize += delegate { Stack(themePage, items); };
        }

        void BuildSettings()
        {
            Button connect = Btn("Connect");
            connect.Click += ConnectClick;
            Button quit = Btn("Quit");
            quit.Click += delegate { mode = DockMode.Quit; ShowPage(); LayoutWindow(); };
            leftBtn = Btn("Left");
            rightBtn = Btn("Right");
            topBtn = Btn("Top");
            minBtn = Btn("Minimize");
            maxBtn = Btn("Maximize");
            leftBtn.Click += delegate { SetEdge("left"); };
            rightBtn.Click += delegate { SetEdge("right"); };
            topBtn.Click += delegate { SetEdge("top"); };
            minBtn.Click += delegate { SetCompact(true); };
            maxBtn.Click += delegate { SetCompact(false); };
            Panel placeRow = ChoiceRow(leftBtn, rightBtn, topBtn);
            Panel sizeRow = ChoiceRow(minBtn, maxBtn);
            Label title = Title("Settings");
            Label hint = Hint("Same Wi-Fi as the phone. Use the address and the 4-character code from Linked Devices.");
            settingsPage.Resize += delegate { Stack(settingsPage, title, hint, hostBox, codeBox, connect, placeRow, sizeRow, reflectBox, connLabel, quit); };
        }

        Panel ChoiceRow(params Button[] buttons)
        {
            Panel row = new Panel();
            row.Height = 36;
            for (int i = 0; i < buttons.Length; i++) row.Controls.Add(buttons[i]);
            row.Resize += delegate
            {
                int gap = 8;
                int w = Math.Max(40, (row.ClientSize.Width - gap * (buttons.Length - 1)) / buttons.Length);
                int x = 0;
                for (int i = 0; i < buttons.Length; i++)
                {
                    buttons[i].SetBounds(x, 0, w, 36);
                    x += w + gap;
                }
            };
            return row;
        }

        void SetEdge(string next)
        {
            edge = next;
            SaveSettings();
            ApplyChrome();
            LayoutWindow();
            Invalidate();
        }

        void SetCompact(bool next)
        {
            compact = next;
            if (compact) mode = DockMode.None;
            SaveSettings();
            ShowPage();
            ApplyChrome();
            LayoutWindow();
            Invalidate();
        }

        void BuildQuit()
        {
            Button stay = Btn("Stay");
            Button leave = Btn("Quit");
            stay.Click += delegate { mode = DockMode.None; ShowPage(); LayoutWindow(); Invalidate(); };
            leave.Click += delegate { Close(); };
            Label title = Title("Quit TrackWindows?");
            Label hint = Hint("The phone keeps what you already saved. This window closes.");
            quitPage.Resize += delegate { Stack(quitPage, title, hint, stay, leave); };
        }

        void ShowPage()
        {
            scanPage.Visible = mode == DockMode.Scan;
            homePage.Visible = mode == DockMode.Home;
            filesPage.Visible = mode == DockMode.Files;
            themePage.Visible = mode == DockMode.Themes;
            settingsPage.Visible = mode == DockMode.Settings;
            quitPage.Visible = mode == DockMode.Quit;
            if (sheet.Visible)
            {
                scanPage.PerformLayout();
                Panel open = OpenPage();
                if (open != null) open.PerformLayout();
            }
        }

        Panel OpenPage()
        {
            if (mode == DockMode.Scan) return scanPage;
            if (mode == DockMode.Home) return homePage;
            if (mode == DockMode.Files) return filesPage;
            if (mode == DockMode.Themes) return themePage;
            if (mode == DockMode.Settings) return settingsPage;
            if (mode == DockMode.Quit) return quitPage;
            return null;
        }

        void ApplyChrome()
        {
            Color card = dark ? Color.FromArgb(20, 20, 24) : Color.FromArgb(244, 247, 255);
            Color ink = dark ? Color.White : Color.FromArgb(24, 24, 32);
            Color muted = dark ? Color.FromArgb(180, 180, 190) : Color.FromArgb(70, 74, 90);
            Color field = dark ? Color.FromArgb(36, 36, 42) : Color.White;
            Color button = dark ? Color.White : Color.FromArgb(24, 24, 32);
            Color buttonInk = dark ? Color.Black : Color.White;
            sheet.BackColor = card;
            Panel[] pages = new Panel[] { scanPage, homePage, filesPage, themePage, settingsPage, quitPage };
            for (int p = 0; p < pages.Length; p++)
            {
                pages[p].BackColor = card;
                foreach (Control control in pages[p].Controls)
                {
                    Label label = control as Label;
                    if (label != null)
                    {
                        label.ForeColor = label.Font.Bold ? ink : muted;
                        label.BackColor = card;
                    }
                    TextBox box = control as TextBox;
                    if (box != null)
                    {
                        box.BackColor = field;
                        box.ForeColor = ink;
                    }
                    Button btn = control as Button;
                    if (btn != null)
                    {
                        btn.BackColor = button;
                        btn.ForeColor = buttonInk;
                    }
                    ListBox list = control as ListBox;
                    if (list != null)
                    {
                        list.BackColor = field;
                        list.ForeColor = ink;
                    }
                    CheckBox check = control as CheckBox;
                    if (check != null)
                    {
                        check.ForeColor = ink;
                        check.BackColor = card;
                    }
                }
            }
            for (int i = 0; i < phoneThemeButtons.Count; i++)
            {
                bool on = string.Equals((string)phoneThemeButtons[i].Tag, phoneTheme, StringComparison.OrdinalIgnoreCase);
                phoneThemeButtons[i].BackColor = on ? Tint(phoneTheme) : button;
                phoneThemeButtons[i].ForeColor = on ? Color.Black : buttonInk;
            }
            PaintChoice(leftBtn, edge == "left");
            PaintChoice(rightBtn, edge == "right");
            PaintChoice(topBtn, edge == "top");
            PaintChoice(minBtn, compact);
            PaintChoice(maxBtn, !compact);
            BackColor = card;
            Invalidate();
        }

        void PaintChoice(Button button, bool on)
        {
            if (button == null) return;
            button.BackColor = on ? Tint(phoneTheme) : (dark ? Color.White : Color.FromArgb(24, 24, 32));
            button.ForeColor = on || dark ? Color.Black : Color.White;
        }

        static Color Tint(string name)
        {
            if (name == "EMERALD") return Color.FromArgb(16, 185, 129);
            if (name == "ELECTRIC_BLUE") return Color.FromArgb(59, 130, 246);
            if (name == "AMBER") return Color.FromArgb(245, 158, 11);
            if (name == "VIOLET") return Color.FromArgb(139, 92, 246);
            if (name == "CYAN") return Color.FromArgb(6, 182, 212);
            return Color.FromArgb(255, 120, 140);
        }

        string SettingsPath()
        {
            string folder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "TrackWindows");
            Directory.CreateDirectory(folder);
            return Path.Combine(folder, "settings.txt");
        }

        void LoadSettings()
        {
            string path = SettingsPath();
            if (!File.Exists(path)) return;
            string[] lines = File.ReadAllLines(path);
            if (lines.Length > 0) hostBox.Text = lines[0];
            if (lines.Length > 1) codeBox.Text = lines[1];
            if (lines.Length > 2) dark = lines[2] == "dark";
            if (lines.Length > 3) reflect = lines[3] == "1";
            if (lines.Length > 4 && (lines[4] == "left" || lines[4] == "right" || lines[4] == "top")) edge = lines[4];
            if (lines.Length > 5) compact = lines[5] == "min";
        }

        void SaveSettings()
        {
            File.WriteAllLines(SettingsPath(), new string[]
            {
                hostBox.Text.Trim(),
                codeBox.Text.Trim(),
                dark ? "dark" : "light",
                reflect ? "1" : "0",
                edge,
                compact ? "min" : "max"
            });
        }

        void MaybeConnect()
        {
            if (hostBox.Text.Trim().Length > 0 && codeBox.Text.Trim().Length >= 4) ConnectClick(this, EventArgs.Empty);
            else
            {
                mode = DockMode.Settings;
                ShowPage();
                LayoutWindow();
            }
        }

        void ConnectClick(object sender, EventArgs e)
        {
            SaveSettings();
            string host = hostBox.Text.Trim();
            string code = codeBox.Text.Trim();
            if (host.Length == 0 || code.Length < 4)
            {
                connLabel.Text = "Type the address and the 4-character code.";
                return;
            }
            connLabel.Text = "Connecting…";
            ThreadPool.QueueUserWorkItem(delegate
            {
                try
                {
                    string baseUrl = Normalize(host);
                    string body = Request(baseUrl + "/api/pair", "POST", "{\"code\":\"" + Esc(code) + "\"}");
                    Dictionary<string, object> map = Parse(body);
                    object tok;
                    map.TryGetValue("token", out tok);
                    string value = tok as string;
                    if (string.IsNullOrEmpty(value))
                    {
                        BeginInvoke(new Action(delegate
                        {
                            connLabel.Text = "That code does not match the phone.";
                            mode = DockMode.Settings;
                            ShowPage();
                            LayoutWindow();
                        }));
                        return;
                    }
                    BeginInvoke(new Action(delegate
                    {
                        hostBase = baseUrl;
                        token = value;
                        connected = true;
                        connLabel.Text = "Live with the phone";
                        SetNotes("Live with the phone");
                        mode = DockMode.None;
                        ShowPage();
                        LayoutWindow();
                        Invalidate();
                    }));
                }
                catch (Exception)
                {
                    BeginInvoke(new Action(delegate
                    {
                        connLabel.Text = "Can't reach the phone. Same Wi-Fi, and Linked Devices left open.";
                        mode = DockMode.Settings;
                        ShowPage();
                        LayoutWindow();
                    }));
                }
            });
        }

        void AddLink(object sender, EventArgs e)
        {
            string url = urlBox.Text.Trim();
            if (url.Length == 0)
            {
                SetNotes("Paste a profile link.");
                return;
            }
            SetNotes("Sending to the phone…");
            Act("scan", url);
        }

        void OpenAccount(object sender, EventArgs e)
        {
            int index = accountList.SelectedIndex;
            if (index < 0 || index >= accountIds.Count) return;
            Act("open", accountIds[index]);
        }

        void OpenFile(object sender, EventArgs e)
        {
            int index = fileList.SelectedIndex;
            if (index < 0 || index >= fileIds.Count || !connected || token == null) return;
            string id = fileIds[index];
            string name = fileNames[index];
            string url = hostBase + "/api/file?t=" + Uri.EscapeDataString(token) + "&id=" + Uri.EscapeDataString(id);
            ThreadPool.QueueUserWorkItem(delegate
            {
                try
                {
                    byte[] bytes = RequestBytes(url);
                    string folder = Path.Combine(Path.GetTempPath(), "TrackWindows");
                    Directory.CreateDirectory(folder);
                    string path = Path.Combine(folder, SafeName(name));
                    File.WriteAllBytes(path, bytes);
                    Process.Start(path);
                }
                catch (Exception)
                {
                    if (!IsDisposed) BeginInvoke(new Action(delegate { SetNotes("Couldn't open that file."); }));
                }
            });
        }

        void Act(string type, string id)
        {
            if (!connected || token == null)
            {
                SetNotes("Connect to the phone first.");
                mode = DockMode.Settings;
                ShowPage();
                LayoutWindow();
                return;
            }
            string payload = "{\"type\":\"" + Esc(type) + "\",\"id\":\"" + Esc(id) + "\"}";
            string url = hostBase + "/api/act?t=" + Uri.EscapeDataString(token);
            ThreadPool.QueueUserWorkItem(delegate
            {
                try
                {
                    Request(url, "POST", payload);
                }
                catch (Exception)
                {
                    if (!IsDisposed) BeginInvoke(new Action(Drop));
                }
            });
        }

        void Poll(object sender, EventArgs e)
        {
            if (!connected || token == null) return;
            if (Interlocked.Exchange(ref busy, 1) == 1) return;
            string url = hostBase + "/api/state?t=" + Uri.EscapeDataString(token);
            ThreadPool.QueueUserWorkItem(delegate
            {
                try
                {
                    string body = Request(url, "GET", null);
                    if (!IsDisposed) BeginInvoke(new Action(delegate { Apply(body); }));
                }
                catch (Exception)
                {
                    if (!IsDisposed) BeginInvoke(new Action(Drop));
                }
                finally
                {
                    Interlocked.Exchange(ref busy, 0);
                }
            });
        }

        void Apply(string body)
        {
            if (!connected) return;
            Dictionary<string, object> map = Parse(body);
            object alive;
            if (map.TryGetValue("alive", out alive) && alive is bool && (bool)alive == false)
            {
                Drop();
                return;
            }
            object theme;
            if (map.TryGetValue("theme", out theme) && theme != null)
            {
                string next = theme.ToString();
                if (!string.Equals(next, phoneTheme, StringComparison.OrdinalIgnoreCase))
                {
                    phoneTheme = next;
                    ApplyChrome();
                }
            }
            object name;
            if (map.TryGetValue("name", out name) && name != null) homeTitle.Text = name.ToString();
            object cost;
            object limit;
            string costText = map.TryGetValue("cost", out cost) && cost != null ? cost.ToString() : "";
            string limitText = map.TryGetValue("limit", out limit) && limit != null ? limit.ToString() : "";
            if (costText.Length > 0)
                activityLine.Text = "Cost this month " + costText + (limitText.Length > 0 ? " of " + limitText : "");
            else
                activityLine.Text = "Cost appears here after expenses are opened on the phone.";
            object note;
            if (map.TryGetValue("note", out note) && note != null && note.ToString().Length > 0) SetNotes(note.ToString());
            FillAccounts(map);
            FillFiles(map);
            connLabel.Text = "Live with the phone";
        }

        void FillAccounts(Dictionary<string, object> map)
        {
            object raw;
            if (!map.TryGetValue("accounts", out raw)) return;
            ArrayList rows = raw as ArrayList;
            if (rows == null) return;
            StringBuilder sig = new StringBuilder();
            List<string> ids = new List<string>();
            List<string> lines = new List<string>();
            foreach (object item in rows)
            {
                Dictionary<string, object> row = item as Dictionary<string, object>;
                if (row == null) continue;
                string id = Field(row, "id");
                string line = Field(row, "platform") + "   " + Field(row, "name");
                string count = Field(row, "count");
                if (count.Length > 0) line += "   " + count + " " + Field(row, "countLabel");
                ids.Add(id);
                lines.Add(line);
                sig.Append(id).Append('|').Append(line).Append('\n');
            }
            string text = sig.ToString();
            if (text == accountSig) return;
            accountSig = text;
            accountIds.Clear();
            accountIds.AddRange(ids);
            accountList.BeginUpdate();
            accountList.Items.Clear();
            for (int i = 0; i < lines.Count; i++) accountList.Items.Add(lines[i]);
            accountList.EndUpdate();
        }

        void FillFiles(Dictionary<string, object> map)
        {
            object raw;
            if (!map.TryGetValue("files", out raw)) return;
            ArrayList rows = raw as ArrayList;
            if (rows == null) return;
            StringBuilder sig = new StringBuilder();
            List<string> ids = new List<string>();
            List<string> names = new List<string>();
            foreach (object item in rows)
            {
                Dictionary<string, object> row = item as Dictionary<string, object>;
                if (row == null) continue;
                string id = Field(row, "id");
                string name = Field(row, "name");
                string size = Field(row, "size");
                string folder = Field(row, "folder");
                ids.Add(id);
                names.Add(name);
                sig.Append(id).Append('|').Append(folder).Append('|').Append(name).Append('|').Append(size).Append('\n');
            }
            string text = sig.ToString();
            if (text == fileSig) return;
            fileSig = text;
            fileIds.Clear();
            fileIds.AddRange(ids);
            fileNames.Clear();
            fileNames.AddRange(names);
            fileList.BeginUpdate();
            fileList.Items.Clear();
            for (int i = 0; i < names.Count; i++)
            {
                string size = "";
                Dictionary<string, object> row = rows[i] as Dictionary<string, object>;
                string folder = "";
                if (row != null)
                {
                    size = Field(row, "size");
                    folder = Field(row, "folder");
                }
                string shown = folder.Length > 0 ? folder + " / " + names[i] : names[i];
                fileList.Items.Add(size.Length > 0 ? shown + "   " + size : shown);
            }
            fileList.EndUpdate();
        }

        void Drop()
        {
            if (!connected) return;
            connected = false;
            token = null;
            accountSig = "";
            fileSig = "";
            accountIds.Clear();
            fileIds.Clear();
            fileNames.Clear();
            accountList.Items.Clear();
            fileList.Items.Clear();
            connLabel.Text = "Left the phone";
            SetNotes("The phone logged out. Connect again with the new code.");
            mode = DockMode.Settings;
            ShowPage();
            LayoutWindow();
            Invalidate();
        }

        void SetNotes(string text)
        {
            for (int i = 0; i < notes.Count; i++) notes[i].Text = text;
        }

        void Step(object sender, EventArgs e)
        {
            bool inside = Bounds.Contains(Cursor.Position);
            int goal = RailOpen;
            if (compact && mode == DockMode.None && !inside) goal = RailShut;
            if (rail == goal) return;
            int step = Math.Max(6, Math.Abs(goal - rail) / 3);
            rail = rail < goal ? Math.Min(goal, rail + step) : Math.Max(goal, rail - step);
            LayoutWindow();
            Invalidate();
        }

        void LayoutWindow()
        {
            if (placing) return;
            placing = true;
            try
            {
                Screen screen = Screen.PrimaryScreen != null ? Screen.PrimaryScreen : Screen.AllScreens[0];
                Rectangle area = screen.WorkingArea;
                int glow = (reflect && rail > RailShut + 20 && mode == DockMode.None) ? 92 : 0;
                int extra = mode == DockMode.None ? glow : SheetW;
                int formW;
                int formH;
                int x;
                int y;
                if (edge == "top")
                {
                    formW = Math.Min(680, area.Width - 24);
                    formH = rail + extra;
                    x = area.Left + Math.Max(0, (area.Width - formW) / 2);
                    y = area.Top + 8;
                }
                else if (edge == "left")
                {
                    formW = rail + extra;
                    formH = Math.Min(580, area.Height - 16);
                    x = area.Left + 8;
                    y = area.Top + Math.Max(0, (area.Height - formH) / 2);
                }
                else
                {
                    formW = rail + extra;
                    formH = Math.Min(580, area.Height - 16);
                    x = area.Right - formW - 10;
                    y = area.Top + Math.Max(0, (area.Height - formH) / 2);
                }
                Bounds = new Rectangle(x, y, formW, formH);
                sheet.Visible = mode != DockMode.None;
                if (sheet.Visible)
                {
                    if (edge == "top") sheet.SetBounds(10, rail + 8, Math.Max(20, formW - 20), Math.Max(20, extra - 16));
                    else if (edge == "left") sheet.SetBounds(rail + 8, 10, Math.Max(20, extra - 16), Math.Max(20, formH - 20));
                    else sheet.SetBounds(10, 10, Math.Max(20, extra - 16), Math.Max(20, formH - 20));
                }
                ShowPage();
                using (GraphicsPath path = Round(new Rectangle(0, 0, ClientSize.Width, ClientSize.Height), ClientSize.Width < 70 ? ClientSize.Width / 2 : 42))
                {
                    Region previous = Region;
                    Region = new Region(path);
                    if (previous != null) previous.Dispose();
                }
            }
            finally
            {
                placing = false;
            }
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            if (compact && rail < 80)
            {
                SetCompact(false);
                return;
            }
            int hit = Hit(e.Location);
            if (hit < 0)
            {
                base.OnMouseDown(e);
                return;
            }
            DockMode next = DockMode.Themes;
            if (hit == 1) next = DockMode.Scan;
            else if (hit == 2) next = DockMode.Home;
            else if (hit == 3) next = DockMode.Files;
            else if (hit == 4) next = DockMode.Settings;
            else if (hit == 5) next = DockMode.Quit;
            if (next != DockMode.Quit && mode == next) mode = DockMode.None;
            else mode = next;
            ShowPage();
            LayoutWindow();
            Invalidate();
        }

        protected override void OnKeyDown(KeyEventArgs e)
        {
            if (e.KeyCode == Keys.Escape && mode != DockMode.None)
            {
                mode = DockMode.None;
                ShowPage();
                LayoutWindow();
                Invalidate();
            }
            base.OnKeyDown(e);
        }

        protected override void OnPaintBackground(PaintEventArgs e)
        {
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            Color card = dark ? Color.FromArgb(20, 20, 24) : Color.FromArgb(232, 238, 252);
            g.Clear(card);
            if (reflect && rail > RailShut + 8 && ClientRectangle.Width > 2 && ClientRectangle.Height > 2)
            {
                using (LinearGradientBrush brush = new LinearGradientBrush(ClientRectangle, Tint(phoneTheme), Color.FromArgb(196, 78, 62), LinearGradientMode.Vertical))
                    g.FillRectangle(brush, ClientRectangle);
            }
            Rectangle railRect = RailRect();
            using (GraphicsPath pill = Round(railRect, rail < 70 ? rail / 2 : 48))
            using (SolidBrush fill = new SolidBrush(dark ? Color.FromArgb(14, 14, 18) : Color.FromArgb(210, 220, 246)))
            using (Pen border = new Pen(Color.FromArgb(235, 255, 255, 255), 3f))
            {
                g.FillPath(fill, pill);
                g.DrawPath(border, pill);
            }
            Color dot = connected ? Color.FromArgb(110, 220, 140) : Color.FromArgb(255, 96, 96);
            using (SolidBrush dotBrush = new SolidBrush(dot))
            {
                Point dotAt = edge == "left"
                    ? new Point(railRect.Left + 8, railRect.Top + 12)
                    : new Point(railRect.Right - 16, edge == "top" ? railRect.Top + (railRect.Height - 8) / 2 : railRect.Top + 12);
                g.FillEllipse(dotBrush, dotAt.X, dotAt.Y, 8, 8);
            }
            if (rail > 80) DrawIcons(g, railRect);
            else DrawStar(g, new Point(railRect.Left + railRect.Width / 2, railRect.Top + railRect.Height / 2), 7, Ink(false));
        }

        void DrawIcons(Graphics g, Rectangle railRect)
        {
            Point[] spots = Spots(railRect);
            bool[] selected = new bool[]
            {
                mode == DockMode.Themes,
                mode == DockMode.Scan,
                mode == DockMode.Home,
                mode == DockMode.Files,
                mode == DockMode.Settings,
                mode == DockMode.Quit
            };
            for (int i = 0; i < spots.Length; i++)
            {
                bool big = i == 0;
                bool on = selected[i];
                int radius = big ? 26 : 20;
                if (big || on)
                {
                    Color bubble = on ? Color.FromArgb(198, 242, 91) : (dark ? Color.FromArgb(29, 29, 42) : Color.White);
                    if (!dark && !on) bubble = Color.FromArgb(236, 242, 255);
                    using (SolidBrush brush = new SolidBrush(bubble))
                        g.FillEllipse(brush, spots[i].X - radius, spots[i].Y - radius, radius * 2, radius * 2);
                }
                Color ink = on ? Color.Black : Ink(false);
                if (i == 0) DrawStar(g, spots[i], 9, ink);
                else if (i == 1) DrawPlus(g, spots[i], ink);
                else if (i == 2) DrawBars(g, spots[i], ink);
                else if (i == 3) DrawFolder(g, spots[i], ink);
                else if (i == 4) DrawSliders(g, spots[i], ink);
                else DrawPower(g, spots[i], ink);
            }
        }

        Color Ink(bool inverted)
        {
            if (inverted) return Color.Black;
            return dark ? Color.White : Color.FromArgb(28, 28, 36);
        }

        Rectangle RailRect()
        {
            if (edge == "top") return new Rectangle(0, 0, ClientSize.Width, Math.Max(1, rail));
            if (edge == "left") return new Rectangle(0, 0, Math.Max(1, rail), ClientSize.Height);
            return new Rectangle(Math.Max(0, ClientSize.Width - rail), 0, Math.Max(1, rail), ClientSize.Height);
        }

        Point[] Spots(Rectangle railRect)
        {
            Point[] spots = new Point[6];
            if (edge == "top")
            {
                int cy = railRect.Top + railRect.Height / 2;
                int left = railRect.Left + 54;
                int right = railRect.Right - 36;
                int gap = Math.Max(48, (right - left) / 5);
                for (int i = 0; i < spots.Length; i++) spots[i] = new Point(left + gap * i, cy);
                return spots;
            }
            int cx = railRect.Left + railRect.Width / 2;
            int top = railRect.Top + 58;
            int bottom = railRect.Bottom - 42;
            int step = Math.Max(48, (bottom - top) / 5);
            for (int i = 0; i < spots.Length; i++) spots[i] = new Point(cx, top + step * i);
            return spots;
        }

        int Hit(Point point)
        {
            if (rail < 80) return -1;
            Point[] spots = Spots(RailRect());
            for (int i = 0; i < spots.Length; i++)
            {
                int dx = point.X - spots[i].X;
                int dy = point.Y - spots[i].Y;
                int radius = i == 0 ? 30 : 24;
                if (dx * dx + dy * dy <= radius * radius) return i;
            }
            return -1;
        }

        static void DrawStar(Graphics g, Point c, int r, Color color)
        {
            Point[] star = new Point[]
            {
                new Point(c.X, c.Y - r),
                new Point(c.X + r / 4, c.Y - r / 4),
                new Point(c.X + r, c.Y),
                new Point(c.X + r / 4, c.Y + r / 4),
                new Point(c.X, c.Y + r),
                new Point(c.X - r / 4, c.Y + r / 4),
                new Point(c.X - r, c.Y),
                new Point(c.X - r / 4, c.Y - r / 4)
            };
            using (SolidBrush brush = new SolidBrush(color)) g.FillPolygon(brush, star);
        }

        static void DrawPlus(Graphics g, Point c, Color color)
        {
            using (SolidBrush brush = new SolidBrush(color))
            {
                g.FillRectangle(brush, c.X - 8, c.Y - 2, 16, 4);
                g.FillRectangle(brush, c.X - 2, c.Y - 8, 4, 16);
            }
        }

        static void DrawBars(Graphics g, Point c, Color color)
        {
            using (SolidBrush brush = new SolidBrush(color))
            {
                g.FillRectangle(brush, c.X - 9, c.Y + 1, 4, 8);
                g.FillRectangle(brush, c.X - 2, c.Y - 6, 4, 15);
                g.FillRectangle(brush, c.X + 5, c.Y - 2, 4, 11);
            }
        }

        static void DrawFolder(Graphics g, Point c, Color color)
        {
            using (SolidBrush brush = new SolidBrush(color))
            {
                g.FillRectangle(brush, c.X - 9, c.Y - 7, 8, 4);
                g.FillRectangle(brush, c.X - 10, c.Y - 3, 20, 13);
            }
        }

        static void DrawSliders(Graphics g, Point c, Color color)
        {
            using (Pen pen = new Pen(color, 2f))
            using (SolidBrush brush = new SolidBrush(color))
            {
                g.DrawLine(pen, c.X - 8, c.Y - 6, c.X + 8, c.Y - 6);
                g.DrawLine(pen, c.X - 8, c.Y, c.X + 8, c.Y);
                g.DrawLine(pen, c.X - 8, c.Y + 6, c.X + 8, c.Y + 6);
                g.FillEllipse(brush, c.X - 2, c.Y - 9, 6, 6);
                g.FillEllipse(brush, c.X + 2, c.Y - 3, 6, 6);
                g.FillEllipse(brush, c.X - 5, c.Y + 3, 6, 6);
            }
        }

        static void DrawPower(Graphics g, Point c, Color color)
        {
            using (Pen pen = new Pen(color, 2f))
            {
                g.DrawArc(pen, c.X - 7, c.Y - 5, 14, 14, 220, 280);
                g.DrawLine(pen, c.X, c.Y - 9, c.X, c.Y + 1);
            }
        }

        static GraphicsPath Round(Rectangle bounds, int radius)
        {
            GraphicsPath path = new GraphicsPath();
            int d = Math.Max(2, radius * 2);
            if (d > bounds.Width) d = Math.Max(2, bounds.Width);
            if (d > bounds.Height) d = Math.Max(2, bounds.Height);
            int x = bounds.X;
            int y = bounds.Y;
            int right = bounds.Right - d;
            int bottom = bounds.Bottom - d;
            path.AddArc(x, y, d, d, 180, 90);
            path.AddArc(right, y, d, d, 270, 90);
            path.AddArc(right, bottom, d, d, 0, 90);
            path.AddArc(x, bottom, d, d, 90, 90);
            path.CloseFigure();
            return path;
        }

        static string Field(Dictionary<string, object> row, string key)
        {
            object value;
            if (!row.TryGetValue(key, out value) || value == null) return "";
            return value.ToString();
        }

        static string Normalize(string host)
        {
            string value = host.Trim();
            if (!value.StartsWith("http://") && !value.StartsWith("https://")) value = "http://" + value;
            while (value.EndsWith("/")) value = value.Substring(0, value.Length - 1);
            return value;
        }

        static string Esc(string value)
        {
            if (value == null) return "";
            return value.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\r", "").Replace("\n", "");
        }

        static string SafeName(string name)
        {
            if (string.IsNullOrEmpty(name)) return "file";
            char[] bad = Path.GetInvalidFileNameChars();
            for (int i = 0; i < bad.Length; i++) name = name.Replace(bad[i], '_');
            return name.Length == 0 ? "file" : name;
        }

        Dictionary<string, object> Parse(string body)
        {
            object parsed = json.DeserializeObject(body);
            Dictionary<string, object> map = parsed as Dictionary<string, object>;
            return map ?? new Dictionary<string, object>();
        }

        static string Request(string url, string method, string body)
        {
            byte[] bytes = RequestBytes(url, method, body, 1200);
            return Encoding.UTF8.GetString(bytes);
        }

        static byte[] RequestBytes(string url)
        {
            return RequestBytes(url, "GET", null, 8000);
        }

        static byte[] RequestBytes(string url, string method, string body, int timeout)
        {
            HttpWebRequest request = (HttpWebRequest)WebRequest.Create(url);
            request.Method = method;
            request.UserAgent = "TrackWindows/1.0 (Windows)";
            request.Timeout = timeout;
            request.ReadWriteTimeout = timeout;
            request.Proxy = null;
            request.ServicePoint.Expect100Continue = false;
            if (body != null)
            {
                byte[] payload = Encoding.UTF8.GetBytes(body);
                request.ContentType = "application/json";
                request.ContentLength = payload.Length;
                using (Stream stream = request.GetRequestStream()) stream.Write(payload, 0, payload.Length);
            }
            using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
            using (Stream stream = response.GetResponseStream())
            using (MemoryStream memory = new MemoryStream())
            {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.Read(buffer, 0, buffer.Length)) > 0) memory.Write(buffer, 0, read);
                return memory.ToArray();
            }
        }

        [STAThread]
        static void Main()
        {
            bool created;
            Mutex gate = new Mutex(true, "Local\\TrackWindows", out created);
            if (!created)
            {
                MessageBox.Show("TrackWindows is already open on the edge of the screen.", "TrackWindows");
                return;
            }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new DockForm());
            gate.ReleaseMutex();
        }
    }
}

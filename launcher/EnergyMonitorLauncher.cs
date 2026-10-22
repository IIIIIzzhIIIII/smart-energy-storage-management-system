using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Text;
using System.Windows.Forms;

namespace EnergyMonitorLauncher
{
    internal static class Program
    {
        private const string MutexName = "Global\\EnergyMonitorLauncher_SingleInstance";

        [STAThread]
        private static void Main()
        {
            bool createdNew;
            using (var mutex = new System.Threading.Mutex(true, MutexName, out createdNew))
            {
                if (!createdNew)
                {
                    MessageBox.Show("光储智慧调度监控平台已经在运行。", "提示",
                        MessageBoxButtons.OK, MessageBoxIcon.Information);
                    return;
                }
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                Application.Run(new LauncherForm());
            }
        }
    }

    internal sealed class LauncherForm : Form
    {
        private readonly string baseDir;
        private readonly string backendUrl = "http://127.0.0.1:8082";
        private readonly string pythonUrl = "http://127.0.0.1:8000/api/health";

        private readonly Label statusLabel;
        private readonly ProgressBar progressBar;
        private readonly Button openButton;
        private readonly Button logButton;
        private readonly TextBox detailBox;
        private readonly Timer pollTimer;

        private Process backendProcess;
        private Process pythonProcess;
        private StreamWriter backendLog;
        private StreamWriter pythonLog;
        private DateTime startedAt;
        private bool ready;
        private bool closing;

        public LauncherForm()
        {
            baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar);
            Text = "光储智慧调度监控平台";
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(560, 310);
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            Font = new Font("Microsoft YaHei UI", 9F);
            BackColor = Color.White;

            var title = new Label
            {
                Text = "光储智慧调度监控平台",
                Font = new Font("Microsoft YaHei UI", 16F, FontStyle.Bold),
                ForeColor = Color.FromArgb(30, 90, 160),
                AutoSize = true,
                Location = new Point(28, 24)
            };
            Controls.Add(title);

            statusLabel = new Label
            {
                Text = "正在启动本地服务，请稍候...",
                AutoSize = false,
                Location = new Point(30, 78),
                Size = new Size(500, 24),
                ForeColor = Color.FromArgb(60, 60, 60)
            };
            Controls.Add(statusLabel);

            progressBar = new ProgressBar
            {
                Location = new Point(30, 108),
                Size = new Size(500, 8),
                Style = ProgressBarStyle.Marquee,
                MarqueeAnimationSpeed = 30
            };
            Controls.Add(progressBar);

            detailBox = new TextBox
            {
                Location = new Point(30, 128),
                Size = new Size(500, 86),
                Multiline = true,
                ReadOnly = true,
                ScrollBars = ScrollBars.Vertical,
                BackColor = Color.FromArgb(248, 249, 251),
                BorderStyle = BorderStyle.FixedSingle,
                Text = "正在准备运行环境..." + Environment.NewLine
            };
            Controls.Add(detailBox);

            openButton = new Button
            {
                Text = "打开系统",
                Location = new Point(30, 232),
                Size = new Size(150, 38),
                Enabled = false,
                BackColor = Color.FromArgb(30, 110, 190),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat
            };
            openButton.FlatAppearance.BorderSize = 0;
            openButton.Click += delegate { OpenBrowser(); };
            Controls.Add(openButton);

            logButton = new Button
            {
                Text = "打开日志目录",
                Location = new Point(196, 232),
                Size = new Size(130, 38),
                FlatStyle = FlatStyle.Flat
            };
            logButton.Click += delegate { OpenLogs(); };
            Controls.Add(logButton);

            var exitButton = new Button
            {
                Text = "退出",
                Location = new Point(400, 232),
                Size = new Size(130, 38),
                FlatStyle = FlatStyle.Flat
            };
            exitButton.Click += delegate { Close(); };
            Controls.Add(exitButton);

            var hint = new Label
            {
                Text = "首次启动会自动建库并生成演示数据，约需 30~90 秒。关闭本窗口将停止服务。",
                AutoSize = false,
                Location = new Point(30, 280),
                Size = new Size(510, 20),
                ForeColor = Color.Gray
            };
            Controls.Add(hint);

            pollTimer = new Timer { Interval = 1000 };
            pollTimer.Tick += delegate { Poll(); };

            Shown += delegate { StartServices(); };
            FormClosing += delegate
            {
                closing = true;
                pollTimer.Stop();
                StopProcess(pythonProcess);
                StopProcess(backendProcess);
                if (backendLog != null) { try { backendLog.Dispose(); } catch { } }
                if (pythonLog != null) { try { pythonLog.Dispose(); } catch { } }
            };
        }


        private void StartServices()
        {
            startedAt = DateTime.Now;
            Directory.CreateDirectory(Path.Combine(baseDir, "data"));
            Directory.CreateDirectory(Path.Combine(baseDir, "logs"));

            if (IsHealthy(backendUrl + "/api/health", 1200))
            {
                Append("检测到 8082 端口已有服务在运行，直接连接。");
            }
            else
            {
                StartBackend();
            }

            if (IsHealthy(pythonUrl, 1200))
            {
                Append("检测到 Python 预测服务已在运行。");
            }
            else
            {
                StartPython();
            }

            pollTimer.Start();
        }

        private void StartBackend()
        {
            string javaExe = Path.Combine(baseDir, "runtime", "bin", "java.exe");
            if (!File.Exists(javaExe))
            {
                javaExe = "java";
                Append("未找到内置 runtime，尝试使用系统 Java。");
            }

            string jar = Path.Combine(baseDir, "app", "energy-monitor.jar");
            if (!File.Exists(jar))
            {
                Append("错误：未找到 app\\energy-monitor.jar，请确认压缩包解压完整。");
                statusLabel.Text = "启动失败：缺少程序文件";
                progressBar.Style = ProgressBarStyle.Blocks;
                return;
            }

            string args = "-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -jar \"" + jar
                + "\" --spring.profiles.active=release";
            backendProcess = StartHidden(javaExe, args, Path.Combine(baseDir, "logs", "backend.log"), out backendLog);
            if (backendProcess == null)
            {
                Append("错误：Java 后端启动失败；请确认 runtime 目录完整，或安装 Java 17。");
                statusLabel.Text = "启动失败：无法启动 Java 后端";
                progressBar.Style = ProgressBarStyle.Blocks;
            }
            else
            {
                Append("Java 后端已启动（端口 8082），正在等待初始化...");
            }
        }

        private void StartPython()
        {
            string exe = Path.Combine(baseDir, "python", "python-ems.exe");
            if (!File.Exists(exe))
            {
                exe = Path.Combine(baseDir, "python", "python-ems", "python-ems.exe");
            }
            if (!File.Exists(exe))
            {
                Append("未找到 Python 预测服务，系统将以基础模式运行（预测/DRO 不可用）。");
                return;
            }
            pythonProcess = StartHidden(exe, "", Path.Combine(baseDir, "logs", "python.log"), out pythonLog);
            if (pythonProcess != null)
            {
                Append("Python 预测服务已启动（端口 8000）。");
            }
        }

        private Process StartHidden(string fileName, string arguments, string logPath, out StreamWriter logWriter)
        {
            logWriter = null;
            try
            {
                var psi = new ProcessStartInfo
                {
                    FileName = fileName,
                    Arguments = arguments,
                    WorkingDirectory = baseDir,
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    StandardOutputEncoding = Encoding.UTF8,
                    StandardErrorEncoding = Encoding.UTF8
                };
                psi.EnvironmentVariables["PYTHONIOENCODING"] = "utf-8";
                psi.EnvironmentVariables["PYTHONUTF8"] = "1";

                var process = new Process { StartInfo = psi, EnableRaisingEvents = true };
                logWriter = new StreamWriter(logPath, true, new UTF8Encoding(false)) { AutoFlush = true };
                StreamWriter writer = logWriter;
                process.OutputDataReceived += delegate(object s, DataReceivedEventArgs e)
                {
                    if (e.Data != null) { try { writer.WriteLine(e.Data); } catch { } }
                };
                process.ErrorDataReceived += delegate(object s, DataReceivedEventArgs e)
                {
                    if (e.Data != null) { try { writer.WriteLine(e.Data); } catch { } }
                };
                process.Start();
                process.BeginOutputReadLine();
                process.BeginErrorReadLine();
                return process;
            }
            catch (Exception ex)
            {
                Append("启动 " + Path.GetFileName(fileName) + " 失败：" + ex.Message);
                if (logWriter != null) { try { logWriter.Dispose(); } catch { } }
                logWriter = null;
                return null;
            }
        }

        private void Poll()
        {
            if (ready || closing) return;

            bool backendOk = IsHealthy(backendUrl + "/api/health", 900);
            bool pythonOk = IsHealthy(pythonUrl, 900);
            int elapsed = (int)(DateTime.Now - startedAt).TotalSeconds;

            if (backendOk)
            {
                ready = true;
                pollTimer.Stop();
                progressBar.Style = ProgressBarStyle.Blocks;
                progressBar.Value = 100;
                statusLabel.Text = "服务已就绪，正在打开浏览器...";
                statusLabel.ForeColor = Color.FromArgb(20, 140, 60);
                openButton.Enabled = true;
                Append("后端健康检查通过（用时 " + elapsed + " 秒）。");
                Append(pythonOk ? "Python 预测服务健康检查通过。" : "Python 预测服务尚未就绪，后端会自动重试连接。");
                OpenBrowser();
            }
            else if (elapsed > 180)
            {
                pollTimer.Stop();
                progressBar.Style = ProgressBarStyle.Blocks;
                statusLabel.Text = "启动超时，请查看 logs\\backend.log";
                statusLabel.ForeColor = Color.Firebrick;
                Append("启动超过 180 秒仍未就绪。常见原因：8082 端口被占用、杀毒软件拦截、解压目录含特殊字符。");
            }
            else if (elapsed % 5 == 0)
            {
                statusLabel.Text = "正在启动本地服务...（已等待 " + elapsed + " 秒）";
            }
        }

        private static bool IsHealthy(string url, int timeoutMs)
        {
            try
            {
                var request = (HttpWebRequest)WebRequest.Create(url);
                request.Method = "GET";
                request.Timeout = timeoutMs;
                request.ReadWriteTimeout = timeoutMs;
                request.Proxy = null;
                using (var response = (HttpWebResponse)request.GetResponse())
                {
                    return response.StatusCode == HttpStatusCode.OK;
                }
            }
            catch
            {
                return false;
            }
        }

        private void OpenBrowser()
        {
            try
            {
                Process.Start(new ProcessStartInfo(backendUrl) { UseShellExecute = true });
                Append("已在默认浏览器打开 " + backendUrl);
            }
            catch (Exception ex)
            {
                Append("打开浏览器失败，请手动访问 " + backendUrl + "：" + ex.Message);
            }
        }

        private void OpenLogs()
        {
            try
            {
                string logs = Path.Combine(baseDir, "logs");
                Directory.CreateDirectory(logs);
                Process.Start(new ProcessStartInfo(logs) { UseShellExecute = true });
            }
            catch { }
        }

        private void Append(string message)
        {
            if (detailBox.IsDisposed) return;
            if (detailBox.InvokeRequired)
            {
                detailBox.BeginInvoke(new Action<string>(Append), message);
                return;
            }
            detailBox.AppendText("[" + DateTime.Now.ToString("HH:mm:ss") + "] " + message + Environment.NewLine);
        }

        private static void StopProcess(Process process)
        {
            if (process == null) return;
            try
            {
                if (!process.HasExited)
                {
                    process.Kill();
                    process.WaitForExit(3000);
                }
            }
            catch { }
            finally
            {
                try { process.Dispose(); } catch { }
            }
        }
    }
}

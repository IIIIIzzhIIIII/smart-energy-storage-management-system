# ============================================================
# 一键运行版构建脚本（Plan C）
#   Windows x64 + 内置 JRE(jlink) + H2 + PyInstaller Python 服务
#
# 用法（在仓库根目录执行）：
#   powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1 -SkipPython
#
# 产物：
#   dist\EnergyMonitor-v1.1.0-win64.zip
#   dist\EnergyMonitor-v1.1.0-win64.zip.sha256
# ============================================================
param(
    [string]$Version = "1.1.0",
    [string]$PythonExe = "",
    [switch]$SkipPython,
    [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$BuildRoot = Join-Path $Root "build\release"
$Stage = Join-Path $BuildRoot "EnergyMonitor"
$DistDir = Join-Path $Root "dist"

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host "==== $Text ====" -ForegroundColor Cyan
}

function Find-Jdk17 {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $jdks = Join-Path $env:USERPROFILE ".jdks"
    if (Test-Path $jdks) {
        $candidates += Get-ChildItem $jdks -Directory | Sort-Object Name -Descending | ForEach-Object { $_.FullName }
    }
    foreach ($homeDir in $candidates) {
        $javac = Join-Path $homeDir "bin\javac.exe"
        if (Test-Path $javac) {
            $version = & $javac -version 2>&1
            if ("$version" -match "17") { return $homeDir }
        }
    }
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($java) {
        $version = & $java.Source -version 2>&1
        if ("$version" -match "version `"17") { return (Split-Path -Parent (Split-Path -Parent $java.Source)) }
    }
    throw "未找到 JDK 17。请安装 JDK 17 并设置 JAVA_HOME 后重试。"
}

function Find-Maven {
    $mvn = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mvn) { return $mvn.Source }
    $local = Join-Path $Root ".tools\apache-maven-3.9.9\bin\mvn.cmd"
    if (Test-Path $local) { return $local }
    Write-Host "未找到 Maven，正在下载到 .tools/ ..."
    $tools = Join-Path $Root ".tools"
    New-Item -ItemType Directory -Force $tools | Out-Null
    $zip = Join-Path $tools "apache-maven-3.9.9-bin.zip"
    if (-not (Test-Path $zip)) {
        Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip" -OutFile $zip
    }
    Expand-Archive -Path $zip -DestinationPath $tools -Force
    return $local
}

function Find-Python {
    if ($PythonExe -and (Test-Path $PythonExe)) { return $PythonExe }
    foreach ($p in @("D:\Python\python.exe", (Join-Path $env:LOCALAPPDATA "Programs\Python\Python313\python.exe"))) {
        if (Test-Path $p) { return $p }
    }
    $cmd = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

$JdkHome = Find-Jdk17
$env:JAVA_HOME = $JdkHome
$env:Path = (Join-Path $JdkHome "bin") + ";" + $env:Path
$Mvn = Find-Maven
$JarTool = Join-Path $JdkHome "bin\jar.exe"
$JLink = Join-Path $JdkHome "bin\jlink.exe"
Write-Host "JDK: $JdkHome"
Write-Host "Maven: $Mvn"

Write-Step "1/7 构建前端"
if ($SkipFrontend) {
    if (-not (Test-Path (Join-Path $Root "frontend\dist\index.html"))) { throw "指定了 -SkipFrontend，但 frontend\dist 不存在。" }
    Write-Host "已跳过前端构建，使用现有 frontend\dist"
} else {
    Push-Location (Join-Path $Root "frontend")
    try {
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw "前端构建失败" }
    } finally { Pop-Location }
}

Write-Step "2/7 打包后端 jar"
Push-Location (Join-Path $Root "backend")
try {
    & $Mvn -B -DskipTests clean package
    if ($LASTEXITCODE -ne 0) { throw "Maven 打包失败" }
} finally { Pop-Location }
$Jar = Join-Path $Root "backend\target\energy-monitor-1.0.0.jar"
if (-not (Test-Path $Jar)) { throw "未生成 $Jar" }

Write-Step "3/7 注入前端静态资源"
$StaticStage = Join-Path $BuildRoot "static-inject"
$StaticTarget = Join-Path $StaticStage "BOOT-INF\classes\static"
Remove-Item $StaticStage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $StaticTarget | Out-Null
Copy-Item (Join-Path $Root "frontend\dist\*") $StaticTarget -Recurse -Force
& $JarTool uf $Jar -C $StaticStage .
if ($LASTEXITCODE -ne 0) { throw "前端资源注入 jar 失败" }
Write-Host "已注入 BOOT-INF/classes/static（index.html / assets）"


Write-Step "4/7 打包 Python 预测服务"
$Python = Find-Python
if (-not $SkipPython) {
    if (-not $Python) {
        Write-Warning "未找到 Python，跳过预测服务打包（生成基础版）"
        $SkipPython = $true
    } else {
        Write-Host "Python: $Python"
        & $Python -c "import PyInstaller" 2>$null
        if ($LASTEXITCODE -ne 0) {
            & $Python -m pip install --disable-pip-version-check -r (Join-Path $Root "python-ems\requirements-build.txt")
            if ($LASTEXITCODE -ne 0) { throw "Python 构建依赖安装失败" }
        }
        Push-Location (Join-Path $Root "python-ems")
        try {
            & $Python -m PyInstaller --noconfirm --clean python-ems.spec
            if ($LASTEXITCODE -ne 0) { throw "PyInstaller 打包失败" }
        } finally { Pop-Location }
    }
}

Write-Step "5/7 组装目录 + jlink 精简 JRE"
Remove-Item $Stage -Recurse -Force -ErrorAction SilentlyContinue
foreach ($dir in @("app", "python", "data", "logs", "docs")) {
    New-Item -ItemType Directory -Force (Join-Path $Stage $dir) | Out-Null
}

Copy-Item $Jar (Join-Path $Stage "app\energy-monitor.jar") -Force
Copy-Item (Join-Path $Root "README.md") (Join-Path $Stage "README.md") -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $Root "docs\一键运行版使用说明.md") (Join-Path $Stage "使用说明.md") -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $Root "docs\一键运行版使用说明.md") (Join-Path $Stage "docs\一键运行版使用说明.md") -Force -ErrorAction SilentlyContinue

if (-not $SkipPython) {
    $PyDist = Join-Path $Root "python-ems\dist\python-ems"
    if (-not (Test-Path (Join-Path $PyDist "python-ems.exe"))) { throw "未找到 PyInstaller 产物 $PyDist\python-ems.exe" }
    Copy-Item (Join-Path $PyDist "*") (Join-Path $Stage "python") -Recurse -Force
    Write-Host "Python 服务已复制到 python\"
}

$modules = @(
    "java.base", "java.logging", "java.naming", "java.sql", "java.transaction.xa",
    "java.xml", "java.management", "java.instrument", "java.net.http",
    "java.security.jgss", "java.rmi", "java.desktop", "jdk.unsupported", "jdk.crypto.ec", "jdk.zipfs"
) -join ","
$runtimeDir = Join-Path $Stage "runtime"
Remove-Item $runtimeDir -Recurse -Force -ErrorAction SilentlyContinue
& $JLink --add-modules $modules --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtimeDir
if ($LASTEXITCODE -ne 0) { throw "jlink 生成运行时失败" }

Write-Step "6/7 编译 Windows 启动器"
$csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path $csc)) { $csc = "C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe" }
if (-not (Test-Path $csc)) { throw "未找到 .NET Framework csc.exe，无法编译启动器" }
$launcherOut = Join-Path $Stage "EnergyMonitor.exe"
& $csc /nologo /codepage:65001 /target:winexe /optimize+ "/out:$launcherOut" `
    /reference:System.dll /reference:System.Drawing.dll /reference:System.Windows.Forms.dll /reference:System.Core.dll `
    (Join-Path $Root "launcher\EnergyMonitorLauncher.cs")
if ($LASTEXITCODE -ne 0) { throw "启动器编译失败" }

@"
此目录用于保存 H2 数据库（energy_monitor.mv.db）。
迁移系统时，请整体复制 data 目录。
首次启动会自动建库并生成演示数据。
"@ | Set-Content (Join-Path $Stage "data\说明.txt") -Encoding UTF8
@"
运行日志：
  backend.log  Java 后端
  python.log   Python 预测服务
"@ | Set-Content (Join-Path $Stage "logs\说明.txt") -Encoding UTF8

Write-Step "7/7 生成压缩包 + SHA256"
New-Item -ItemType Directory -Force $DistDir | Out-Null
$zip = Join-Path $DistDir ("EnergyMonitor-v{0}-win64{1}.zip" -f $Version, $(if ($SkipPython) { "-basic" } else { "" }))
Remove-Item $zip -Force -ErrorAction SilentlyContinue
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipped = $false
for ($i = 1; $i -le 3; $i++) {
    try {
        [System.IO.Compression.ZipFile]::CreateFromDirectory(
            $Stage, $zip,
            [System.IO.Compression.CompressionLevel]::Optimal,
            $false)
        $zipped = $true
        break
    } catch {
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
        if ($i -eq 3) { throw "压缩失败（文件可能被杀毒软件占用）：$($_.Exception.Message)" }
        Write-Warning "压缩被占用，3 秒后重试（$i/3）"
        Start-Sleep -Seconds 3
    }
}
if (-not $zipped) { throw "压缩失败" }
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash
"$hash  $(Split-Path -Leaf $zip)" | Set-Content ($zip + ".sha256") -Encoding ASCII

$sizeMb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host ""
Write-Host "构建完成：$zip ($sizeMb MB)" -ForegroundColor Green
Write-Host "SHA256：$hash"
if ($SkipPython) {
    Write-Host "注意：本次为基础版（无 Python 预测/DRO）。" -ForegroundColor Yellow
}


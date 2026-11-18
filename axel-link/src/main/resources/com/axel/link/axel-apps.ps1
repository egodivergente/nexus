# AXEL Link: estado real y apertura de aplicaciones de escritorio en Windows.
# Se ejecuta a traves de AXEL Commander (process.run, sin shell) y escribe un unico JSON en UTF-8.
# Nada de rutas fijas: cada aplicacion se localiza en el registro, los accesos directos o los paquetes MSIX.
param(
  [ValidateSet('status', 'open')][string]$Action = 'status',
  [string]$Id = 'all'
)
$ErrorActionPreference = 'Stop'
$script:Started = Get-Date
$script:Dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$script:Name = Split-Path -Leaf $MyInvocation.MyCommand.Path

$helper = @"
using System;
using System.Text;
using System.Diagnostics;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public static class AxelWin {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc p, IntPtr l);
  [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] static extern bool IsIconic(IntPtr h);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] static extern IntPtr GetWindow(IntPtr h, uint c);
  [DllImport("user32.dll", EntryPoint = "GetWindowLongW")] static extern int GetWindowLong(IntPtr h, int i);
  [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] static extern bool BringWindowToTop(IntPtr h);
  [DllImport("user32.dll")] static extern bool AttachThreadInput(uint a, uint b, bool f);
  [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();
  [DllImport("user32.dll")] static extern uint SendInput(uint n, byte[] inputs, int size);
  [DllImport("dwmapi.dll")] static extern int DwmGetWindowAttribute(IntPtr h, int a, out int v, int s);
  [DllImport("kernel32.dll")] static extern IntPtr OpenProcess(uint access, bool inherit, uint pid);
  [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr h);
  [DllImport("kernel32.dll", CharSet = CharSet.Unicode)] static extern bool QueryFullProcessImageName(IntPtr h, uint flags, StringBuilder name, ref uint size);
  [DllImport("ntdll.dll")] static extern int NtQueryInformationProcess(IntPtr h, int kind, IntPtr buffer, int length, out int needed);

  public class Win { public long Handle; public uint Pid; public string Title; public bool Minimized; public bool Foreground; }
  public class Proc { public uint Pid; public string Name; public string Path; public string CommandLine; }

  // Ventanas reales de aplicacion: visibles, con titulo, sin propietario, no de herramientas y no ocultas por DWM.
  public static List<Win> List() {
    var found = new List<Win>();
    IntPtr front = GetForegroundWindow();
    EnumWindows((h, l) => {
      if (!IsWindowVisible(h)) return true;
      if (GetWindow(h, 4) != IntPtr.Zero) return true;
      if ((GetWindowLong(h, -20) & 0x80) != 0) return true;
      int cloaked = 0;
      if (DwmGetWindowAttribute(h, 14, out cloaked, 4) == 0 && cloaked != 0) return true;
      var text = new StringBuilder(512);
      GetWindowText(h, text, 512);
      if (text.Length == 0) return true;
      uint pid;
      GetWindowThreadProcessId(h, out pid);
      found.Add(new Win { Handle = h.ToInt64(), Pid = pid, Title = text.ToString(), Minimized = IsIconic(h), Foreground = h == front });
      return true;
    }, IntPtr.Zero);
    return found;
  }

  // Procesos con la ruta de su ejecutable. La linea de ordenes solo se lee para los nombres pedidos.
  public static List<Proc> Processes(string[] withCommandLine) {
    var wanted = new HashSet<string>(withCommandLine ?? new string[0], StringComparer.OrdinalIgnoreCase);
    var found = new List<Proc>();
    foreach (var process in Process.GetProcesses()) {
      try {
        var item = new Proc { Pid = (uint)process.Id, Name = process.ProcessName + ".exe" };
        IntPtr h = OpenProcess(0x1000, false, item.Pid);
        if (h != IntPtr.Zero) {
          try {
            var text = new StringBuilder(1024);
            uint size = 1024;
            if (QueryFullProcessImageName(h, 0, text, ref size)) item.Path = text.ToString();
            if (wanted.Contains(item.Name)) item.CommandLine = CommandLine(h);
          } finally { CloseHandle(h); }
        }
        found.Add(item);
      } catch (Exception) {
      } finally { process.Dispose(); }
    }
    return found;
  }

  static string CommandLine(IntPtr h) {
    int needed;
    NtQueryInformationProcess(h, 60, IntPtr.Zero, 0, out needed);
    if (needed <= 0 || needed > (1 << 20)) return null;
    IntPtr buffer = Marshal.AllocHGlobal(needed);
    try {
      if (NtQueryInformationProcess(h, 60, buffer, needed, out needed) != 0) return null;
      int length = Marshal.ReadInt16(buffer) & 0xFFFF;
      IntPtr text = Marshal.ReadIntPtr(buffer, IntPtr.Size);
      return text == IntPtr.Zero ? null : Marshal.PtrToStringUni(text, length / 2);
    } finally { Marshal.FreeHGlobal(buffer); }
  }

  static bool Front(IntPtr h) {
    for (int i = 0; i < 10; i++) {
      if (GetForegroundWindow() == h) return true;
      System.Threading.Thread.Sleep(50);
    }
    return false;
  }

  // Restaura y trae al frente. Devuelve si Windows la dejo realmente en primer plano.
  public static bool Activate(long handle) {
    IntPtr h = new IntPtr(handle);
    if (IsIconic(h)) ShowWindow(h, 9);
    if (GetForegroundWindow() == h) return true;
    SetForegroundWindow(h);
    if (Front(h)) return true;
    // Una entrada de raton vacia (sin movimiento ni clic) levanta el bloqueo de primer plano.
    int size = IntPtr.Size == 8 ? 40 : 28;
    SendInput(1, new byte[size], size);
    SetForegroundWindow(h);
    if (Front(h)) return true;
    uint other;
    uint target = GetWindowThreadProcessId(GetForegroundWindow(), out other);
    uint me = GetCurrentThreadId();
    if (target != 0 && target != me) {
      AttachThreadInput(me, target, true);
      BringWindowToTop(h);
      SetForegroundWindow(h);
      AttachThreadInput(me, target, false);
    }
    return Front(h);
  }
}
"@

# Compilar el ayudante es lo mas caro del guion: se compila una vez y se guarda junto a el con su huella.
function Import-Helper {
  $sha = [BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($helper))).Replace('-', '').Substring(0, 12).ToLowerInvariant()
  $library = Join-Path $script:Dir ('axel-win-' + $sha + '.dll')
  if (Test-Path -LiteralPath $library) {
    try { Add-Type -Path $library; return } catch { }
  }
  try {
    $fresh = Join-Path $script:Dir ('axel-win-' + $sha + '.' + $PID + '.dll')
    Add-Type -TypeDefinition $helper -OutputAssembly $fresh -OutputType Library
    try { Move-Item -LiteralPath $fresh -Destination $library -Force } catch { Remove-Item -LiteralPath $fresh -Force -ErrorAction SilentlyContinue }
    Add-Type -Path $library
  } catch {
    Add-Type -TypeDefinition $helper
  }
}
Import-Helper

function Get-Shortcuts([string]$Pattern) {
  $folders = @(
    [Environment]::GetFolderPath('Desktop'),
    [Environment]::GetFolderPath('CommonDesktopDirectory'),
    [Environment]::GetFolderPath('Programs'),
    [Environment]::GetFolderPath('CommonPrograms')
  ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
  $shell = New-Object -ComObject WScript.Shell
  foreach ($folder in $folders) {
    Get-ChildItem -LiteralPath $folder -Filter '*.lnk' -Recurse -ErrorAction SilentlyContinue |
      Where-Object { $_.BaseName -like $Pattern } |
      ForEach-Object {
        $link = $shell.CreateShortcut($_.FullName)
        [pscustomobject]@{ Path = $_.FullName; Target = $link.TargetPath; Arguments = $link.Arguments }
      }
  }
}

function Find-Cli([string]$Name) {
  $command = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($command) { return $command.Source }
  return $null
}

function Find-Package([string]$Name) {
  $package = @(Get-AppxPackage -Name $Name -ErrorAction SilentlyContinue | Sort-Object { [version]$_.Version } -Descending)[0]
  if (-not $package) { return $null }
  $entry = @((Get-AppxPackageManifest $package).Package.Applications.Application)[0]
  if (-not $entry) { return $null }
  return [pscustomobject]@{
    Aumid = $package.PackageFamilyName + '!' + $entry.Id
    Root = $package.InstallLocation
    Version = [string]$package.Version
  }
}

function Get-Stamp([string]$Path) {
  try { return [string](Get-Item -LiteralPath $Path).LastWriteTimeUtc.Ticks } catch { return $null }
}

# --- Definiciones: cada una devuelve datos. launch dice como se inicia; root o profile, como se reconocen sus procesos ---

function Resolve-Agy {
  $candidates = New-Object System.Collections.Generic.List[string]
  foreach ($key in 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                   'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                   'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*') {
    Get-ItemProperty $key -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -like 'Antigravity*' } | ForEach-Object {
      if ($_.InstallLocation) { $candidates.Add((Join-Path $_.InstallLocation 'Antigravity.exe')) }
      if ($_.DisplayIcon) { $candidates.Add(($_.DisplayIcon -replace ',-?\d+$', '').Trim('"')) }
    }
  }
  Get-Shortcuts 'Antigravity*' | ForEach-Object { if ($_.Target) { $candidates.Add($_.Target) } }
  $exe = $candidates | Where-Object { $_ -like '*\Antigravity.exe' -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
  $item = @{ id = 'agy'; name = 'Google Antigravity'; cli = Find-Cli 'agy'; installed = [bool]$exe }
  if ($exe) { $item.launch = 'exe'; $item.target = $exe; $item.root = Split-Path -Parent $exe; $item.stamp = Get-Stamp $exe }
  return $item
}

function Resolve-Packaged([string]$Id, [string]$Name, [string]$Package, [string]$Cli, [string]$Fallback) {
  $item = @{ id = $Id; name = $Name; cli = Find-Cli $Cli; installed = $false }
  $found = Find-Package $Package
  if ($found) {
    $item.installed = $true; $item.launch = 'msix'; $item.target = $found.Aumid; $item.root = $found.Root; $item.version = $found.Version
  } elseif ($Fallback -and (Test-Path -LiteralPath $Fallback)) {
    $item.installed = $true; $item.launch = 'exe'; $item.target = $Fallback; $item.root = Split-Path -Parent $Fallback; $item.stamp = Get-Stamp $Fallback
  }
  return $item
}

function Resolve-Nexus {
  $item = @{ id = 'nexus'; name = 'Nexus Desktop'; cli = $null; installed = $false }
  $link = Get-Shortcuts 'Nexus Studio' | Where-Object { $_.Arguments -match 'local_host\.py' } | Select-Object -First 1
  if (-not $link) { return $item }
  $script = if ($link.Arguments -match '"([^"]*local_host\.py)"') { $Matches[1] } else { $null }
  if (-not $script -or -not (Test-Path -LiteralPath $script) -or -not (Test-Path -LiteralPath $link.Target)) { return $item }
  $data = if ($link.Arguments -match '--data-dir\s+"([^"]+)"') { $Matches[1] }
          elseif ($link.Arguments -match '--data-dir\s+(\S+)') { $Matches[1] }
          else { Join-Path $env:LOCALAPPDATA 'NexusDesktop' }
  $item.installed = $true; $item.launch = 'shortcut'; $item.target = $link.Path; $item.stamp = Get-Stamp $link.Path
  $item.version = Split-Path -Leaf (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $script)))
  # La ventana es Edge en modo aplicacion con el perfil privado de Nexus; el servicio es el host de Python.
  $item.data = $data; $item.profile = (Join-Path $data 'EdgeProfile')
  return $item
}

$resolvers = [ordered]@{
  agy    = { Resolve-Agy }
  claude = { Resolve-Packaged 'claude' 'Claude' 'Claude' 'claude' (Join-Path $env:LOCALAPPDATA 'AnthropicClaude\claude.exe') }
  codex  = { Resolve-Packaged 'codex' 'Codex' 'OpenAI.Codex' 'codex' $null }
  nexus  = { Resolve-Nexus }
}

# --- Memoria de lo localizado: buscar en el registro, los accesos directos y los paquetes cuesta segundos. ---
# Al consultar se reutiliza mientras el destino siga ahi y sin cambios; al abrir se localiza siempre de nuevo.

$cacheFile = Join-Path $script:Dir 'axel-apps-cache.json'
$cache = @{}
try {
  $saved = Get-Content -LiteralPath $cacheFile -Raw -Encoding UTF8 | ConvertFrom-Json
  if ($saved.schema -eq 1 -and $saved.script -eq $script:Name) {
    foreach ($entry in $saved.apps.PSObject.Properties) { $cache[$entry.Name] = $entry.Value }
  }
} catch { }
$cacheChanged = $false

function Test-Current($saved) {
  if (-not $saved -or -not $saved.item) { return $false }
  $age = ((Get-Date).ToUniversalTime() - ([datetime]$saved.at).ToUniversalTime()).TotalMinutes
  if ($age -lt 0) { return $false }
  $item = $saved.item
  # Lo que no estaba instalado se vuelve a buscar pronto; lo instalado, dos veces al dia o si cambia.
  if (-not $item.installed) { return $age -lt 10 }
  if ($age -gt 720) { return $false }
  if ($item.launch -eq 'msix') { return (Test-Path -LiteralPath $item.root) }
  return ((Get-Stamp $item.target) -eq $item.stamp -and $item.stamp)
}

function Get-Item-For([string]$Key, [bool]$Fresh) {
  if (-not $Fresh -and (Test-Current $cache[$Key])) { return $cache[$Key].item }
  $item = & $resolvers[$Key]
  $cache[$Key] = [pscustomobject]@{ at = (Get-Date).ToUniversalTime().ToString('o'); item = [pscustomobject]$item }
  $script:cacheChanged = $true
  return $cache[$Key].item
}

function Save-Cache {
  if (-not $script:cacheChanged) { return }
  try {
    $apps = [ordered]@{}
    foreach ($key in $cache.Keys) { $apps[$key] = $cache[$key] }
    $text = [ordered]@{ schema = 1; script = $script:Name; apps = $apps } | ConvertTo-Json -Depth 6 -Compress
    $fresh = $cacheFile + '.' + $PID
    [IO.File]::WriteAllText($fresh, $text, (New-Object Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $fresh -Destination $cacheFile -Force
  } catch { }
}

# --- Estado ---

function Get-Processes {
  $list = [AxelWin]::Processes([string[]]@('msedge.exe'))
  # Si Windows no deja leer la linea de ordenes por la via rapida, se pregunta a WMI solo por Edge.
  $blind = $false
  foreach ($p in $list) { if ($p.Name -eq 'msedge.exe' -and -not $p.CommandLine) { $blind = $true; break } }
  if ($blind) {
    try {
      $lines = @{}
      Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" -Property ProcessId, CommandLine | ForEach-Object { $lines[[uint32]$_.ProcessId] = $_.CommandLine }
      foreach ($p in $list) { if ($p.Name -eq 'msedge.exe' -and -not $p.CommandLine) { $p.CommandLine = $lines[$p.Pid] } }
    } catch { }
  }
  return $list
}

function Get-Owned($item, $processes) {
  $ids = @{}
  if ($item.root) {
    $prefix = $item.root.TrimEnd('\') + '\'
    foreach ($p in $processes) {
      if ($p.Path -and $p.Path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { $ids[$p.Pid] = $true }
    }
  } elseif ($item.profile) {
    $needle = '--user-data-dir=' + $item.profile
    foreach ($p in $processes) {
      if ($p.Name -ne 'msedge.exe' -or -not $p.CommandLine) { continue }
      if ($p.CommandLine.IndexOf('--type=', [StringComparison]::Ordinal) -ge 0) { continue }
      if ($p.CommandLine.IndexOf($needle, [StringComparison]::OrdinalIgnoreCase) -ge 0) { $ids[$p.Pid] = $true }
    }
  }
  return $ids
}

function Test-Service($item) {
  if (-not $item.data) { return $false }
  try {
    $session = Get-Content -LiteralPath (Join-Path $item.data 'local-session.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $request = [Net.HttpWebRequest]::Create('http://127.0.0.1:' + [int]$session.port + '/health')
    $request.Timeout = 1200; $request.Proxy = $null
    $response = $request.GetResponse()
    try { return (New-Object IO.StreamReader($response.GetResponseStream())).ReadToEnd() -eq '{"app":"Nexus Studio"}' }
    finally { $response.Close() }
  } catch { return $false }
}

function Get-Snapshot($item, $processes = $null, $windows = $null) {
  if ($null -eq $processes) { $processes = Get-Processes }
  if ($null -eq $windows) { $windows = [AxelWin]::List() }
  $ids = Get-Owned $item $processes
  $mine = New-Object System.Collections.Generic.List[object]
  foreach ($w in $windows) { if ($ids.ContainsKey($w.Pid)) { $mine.Add($w) } }
  # El servicio solo decide entre segundo plano y cerrada: con una ventana a la vista no hace falta preguntarle.
  $service = if ($mine.Count -eq 0 -and $ids.Count -eq 0) { Test-Service $item } else { [bool]$item.data }
  $state = if ($mine.Count -gt 0) { 'open' } elseif ($ids.Count -gt 0 -or $service) { 'background' } else { 'closed' }
  return @{ state = $state; processes = $ids.Count; windows = $mine.ToArray(); service = $service }
}

function Start-App($item) {
  if ($item.launch -eq 'msix') { Start-Process -FilePath ('shell:AppsFolder\' + $item.target) }
  else { Start-Process -FilePath $item.target }
}

function Invoke-Open($item) {
  $result = @{ did = 'none'; confirmed = $false; foreground = $false; error = $null }
  $gate = New-Object Threading.Mutex($false, ('Local\AxelLinkOpen-' + $item.id))
  $held = $false
  try {
    try { $held = $gate.WaitOne(30000) } catch [Threading.AbandonedMutexException] { $held = $true }
    if (-not $held) { $result.error = 'busy'; return $result }
    $now = Get-Snapshot $item
    if ($now.state -ne 'open') {
      Start-App $item | Out-Null
      $result.did = 'launched'
      $limit = (Get-Date).AddSeconds(30)
      do { Start-Sleep -Milliseconds 400; $now = Get-Snapshot $item } while ($now.state -ne 'open' -and (Get-Date) -lt $limit)
      if ($now.state -ne 'open') { $result.error = 'no_window'; return $result }
    } else {
      $result.did = 'activated'
    }
    $result.confirmed = $true
    $window = @($now.windows | Sort-Object @{ Expression = { $_.Foreground }; Descending = $true }, Minimized)[0]
    $result.foreground = [AxelWin]::Activate($window.Handle)
    return $result
  } catch {
    $result.error = $_.Exception.Message
    return $result
  } finally {
    if ($held) { $gate.ReleaseMutex() }
    $gate.Dispose()
  }
}

$wanted = if ($Id -eq 'all') { @($resolvers.Keys) } else { @($Id.Split(',') | ForEach-Object { $_.Trim().ToLowerInvariant() }) }
$report = New-Object System.Collections.Generic.List[object]
# Una sola lectura de procesos y ventanas para el estado; al abrir se vuelve a leer despues de actuar.
$sharedProcesses = $null; $sharedWindows = $null
if ($Action -eq 'status') { $sharedProcesses = Get-Processes; $sharedWindows = [AxelWin]::List() }
foreach ($key in $wanted) {
  if (-not $resolvers.Contains($key)) { $report.Add([ordered]@{ id = $key; state = 'unknown'; error = 'unknown_app' }); continue }
  $entry = [ordered]@{ id = $key }
  try {
    $item = Get-Item-For $key ($Action -eq 'open')
    $entry.name = $item.name
    $entry.installed = [bool]$item.installed
    $entry.launch = $item.launch
    $entry.target = $item.target
    $entry.version = $item.version
    $entry.cli = $item.cli
    if (-not $item.installed) {
      $entry.state = 'missing'
    } else {
      if ($Action -eq 'open') { $entry.result = Invoke-Open $item }
      $now = Get-Snapshot $item $sharedProcesses $sharedWindows
      $entry.state = $now.state
      $entry.processes = $now.processes
      $entry.service = $now.service
      $entry.windows = @($now.windows | ForEach-Object {
        [ordered]@{ title = $_.Title.Substring(0, [Math]::Min(80, $_.Title.Length)); minimized = $_.Minimized; foreground = $_.Foreground; pid = $_.Pid }
      })
    }
  } catch {
    $entry.state = 'unknown'
    $entry.error = $_.Exception.Message
  }
  $report.Add($entry)
}
Save-Cache
$output = [ordered]@{
  schema = 1
  host = $env:COMPUTERNAME
  action = $Action
  at = (Get-Date).ToString('o')
  ms = [int]((Get-Date) - $script:Started).TotalMilliseconds
  apps = $report.ToArray()
}
$bytes = [Text.Encoding]::UTF8.GetBytes(($output | ConvertTo-Json -Depth 6 -Compress))
$stream = [Console]::OpenStandardOutput()
$stream.Write($bytes, 0, $bytes.Length)
$stream.Flush()

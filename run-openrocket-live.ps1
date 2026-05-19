$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
$gradleWrapper = Join-Path $repoRoot 'gradlew.bat'
$installRoot = Join-Path $repoRoot 'build\install\info.openrocket'
$libPath = Join-Path $installRoot 'lib\*'
$coreJarPath = Join-Path $installRoot 'lib\core-26.xx-SNAPSHOT.jar'
$swingJarPath = Join-Path $installRoot 'lib\swing-26.xx-SNAPSHOT.jar'
$logDirectory = Join-Path $env:USERPROFILE '.openrocket'
$activityLogPath = Join-Path $logDirectory 'ai-automation.log'

function Write-LauncherLog([string] $message) {
    $timestamp = Get-Date -Format o
    Add-Content -Path $activityLogPath -Value "$timestamp [launcher] $message"
}

function Get-LatestWriteTimeUtc([string[]] $paths, [string[]] $includePatterns) {
    $latest = [DateTime]::MinValue
    foreach ($path in $paths) {
        if (-not (Test-Path $path)) {
            continue
        }

        $item = Get-Item $path
        $candidates = if ($item.PSIsContainer) {
            Get-ChildItem -Path $path -Recurse -File -Include $includePatterns
        } else {
            @($item)
        }

        foreach ($candidate in $candidates) {
            if ($candidate.LastWriteTimeUtc -gt $latest) {
                $latest = $candidate.LastWriteTimeUtc
            }
        }
    }

    return $latest
}

function Test-InstallRefreshNeeded {
    $distributionTargets = @($coreJarPath, $swingJarPath)
    foreach ($target in $distributionTargets) {
        if (-not (Test-Path $target)) {
            return $true
        }
    }

    $sourcePaths = @(
        (Join-Path $repoRoot 'settings.gradle'),
        (Join-Path $repoRoot 'build.gradle'),
        (Join-Path $repoRoot 'core\build.gradle'),
        (Join-Path $repoRoot 'swing\build.gradle'),
        (Join-Path $repoRoot 'core\src\main'),
        (Join-Path $repoRoot 'swing\src\main')
    )
    $latestSource = Get-LatestWriteTimeUtc -paths $sourcePaths -includePatterns @('*.java', '*.gradle', '*.kts', '*.properties', '*.xml', '*.json')
    $latestInstall = Get-LatestWriteTimeUtc -paths $distributionTargets -includePatterns @('*')
    return $latestSource -gt $latestInstall
}

function Resolve-JavaExecutable {
    if ($env:JAVA_HOME) {
        $javawFromHome = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
        if (Test-Path $javawFromHome) {
            return $javawFromHome
        }

        $javaFromHome = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path $javaFromHome) {
            return $javaFromHome
        }
    }

    $javawCommand = Get-Command 'javaw.exe' -ErrorAction SilentlyContinue
    if ($javawCommand) {
        return $javawCommand.Source
    }

    $javaCommand = Get-Command 'java.exe' -ErrorAction SilentlyContinue
    if ($javaCommand) {
        return $javaCommand.Source
    }

    throw 'Java launcher not found. Install Java 17+ and make sure JAVA_HOME or PATH is set.'
}

if (-not (Test-Path $gradleWrapper)) {
    throw "Gradle wrapper not found at '$gradleWrapper'."
}

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
if (-not (Test-Path $activityLogPath)) {
    New-Item -ItemType File -Path $activityLogPath -Force | Out-Null
}

Write-LauncherLog 'starting OpenRocket live session'

$tailLogPath = $activityLogPath.Replace("'", "''")
$tailCommand = @"
`$ErrorActionPreference = 'Stop'
`$Host.UI.RawUI.WindowTitle = 'OpenRocket AI Console'
`$logPath = '$tailLogPath'
if (-not (Test-Path `$logPath)) {
    New-Item -ItemType File -Path `$logPath -Force | Out-Null
}
Write-Host 'OpenRocket AI Console'
Write-Host "Tailing `$logPath"
Write-Host ''
Get-Content -Path `$logPath -Tail 50 -Wait
"@

Start-Process -FilePath 'powershell.exe' `
    -ArgumentList @('-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $tailCommand) `
    -WorkingDirectory $repoRoot

$javaExe = Resolve-JavaExecutable

if (Test-InstallRefreshNeeded) {
    Write-LauncherLog 'detected newer source files, running gradlew installDist -PskipExternalComponents=true'
    Push-Location $repoRoot
    try {
        & $gradleWrapper 'installDist' '-PskipExternalComponents=true'
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle installDist failed with exit code $LASTEXITCODE."
        }
        Write-LauncherLog 'installDist refresh completed successfully'
    } catch {
        Write-LauncherLog "installDist refresh failed: $($_.Exception.Message)"
        throw
    } finally {
        Pop-Location
    }
} else {
    Write-LauncherLog 'existing installDist is already current'
}

if (-not (Test-Path $installRoot)) {
    throw "OpenRocket install directory not found at '$installRoot'."
}

$arguments = New-Object System.Collections.Generic.List[string]
$arguments.Add("-Duser.home=$env:USERPROFILE")
$arguments.Add('-Dopenrocket.remote.enabled=true')
$arguments.Add("-Dopenrocket.remote.activityLogPath=$activityLogPath")

if ($env:OPENROCKET_REMOTE_PORT) {
    $arguments.Add("-Dopenrocket.remote.port=$($env:OPENROCKET_REMOTE_PORT)")
}

if ($env:OPENROCKET_REMOTE_TOKEN) {
    $arguments.Add("-Dopenrocket.remote.token=$($env:OPENROCKET_REMOTE_TOKEN)")
}

$arguments.Add('-cp')
$arguments.Add($libPath)
$arguments.Add('info.openrocket.swing.startup.OpenRocket')

foreach ($arg in $args) {
    $arguments.Add([string] $arg)
}

Start-Process -FilePath $javaExe -ArgumentList $arguments -WorkingDirectory $installRoot

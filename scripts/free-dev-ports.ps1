param(
    [int[]]$Ports = @(8080, 5173),
    [switch]$Force
)

function Get-PortProcessInfo {
    param([int[]]$Ports)

    $connections = foreach ($port in $Ports) {
        Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    }

    $connections |
        Where-Object { $_ -and $_.OwningProcess } |
        Sort-Object LocalPort, OwningProcess -Unique |
        ForEach-Object {
            $process = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue
            $path = $null
            try {
                $path = $process.MainModule.FileName
            } catch {
                $path = $null
            }

            [PSCustomObject]@{
                Port = $_.LocalPort
                Pid = $_.OwningProcess
                ProcessName = if ($process) { $process.ProcessName } else { "unknown" }
                Path = $path
            }
        }
}

$busy = @(Get-PortProcessInfo -Ports $Ports)

if ($busy.Count -eq 0) {
    Write-Host "No listeners found on dev ports: $($Ports -join ', ')."
    exit 0
}

Write-Host "The following processes are listening on dev ports:"
$busy | Format-Table -AutoSize

if (-not $Force) {
    $answer = Read-Host "Stop these processes? Type YES to continue"
    if ($answer -ne "YES") {
        Write-Host "No processes stopped."
        exit 1
    }
}

$pids = $busy | Select-Object -ExpandProperty Pid -Unique
foreach ($pidValue in $pids) {
    try {
        Stop-Process -Id $pidValue -Force -ErrorAction Stop
        Write-Host "Stopped PID $pidValue."
    } catch {
        Write-Warning "Could not stop PID ${pidValue}: $($_.Exception.Message)"
    }
}

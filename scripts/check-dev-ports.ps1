param(
    [int[]]$Ports = @(8080, 5173)
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
    Write-Host "Dev ports are available: $($Ports -join ', ')."
    exit 0
}

Write-Host "Cannot start dev servers because these ports are already in use:"
$busy | Format-Table -AutoSize
Write-Host "Run 'npm run dev:free-ports' to review and stop these processes, or close them manually."
exit 1

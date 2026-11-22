param(
    [int]$Count = 0,
    [double]$Interval = 0.2,
    [string]$Group = "",
    [string]$Log = "",
    [int]$Window = 20,
    [string]$Path = "/api/wallets/version"
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
Add-Type -AssemblyName System.Net.Http

$address = kubectl get gateway eg -n minecoin -o jsonpath='{.status.addresses[0].value}'
$nodePort = kubectl get service minecoin-gateway -n envoy-gateway-system -o jsonpath='{.spec.ports[0].nodePort}'
if (-not $address -or -not $nodePort) {
    throw "Не найден адрес Gateway eg: проверьте kubectl get gateway eg -n minecoin"
}
$url = "http://${address}:${nodePort}${Path}"

$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(5)
if ($Group) {
    $client.DefaultRequestHeaders.Add("x-user-group", $Group)
}

$writer = $null
if ($Log) {
    $directory = Join-Path (Get-Location) "k8s/strategies/logs"
    New-Item -ItemType Directory -Force $directory | Out-Null
    $writer = New-Object System.IO.StreamWriter((Join-Path $directory "$Log.log"), $true, (New-Object System.Text.UTF8Encoding $false))
    $writer.AutoFlush = $true
}

function Write-Line([string]$Text, [string]$Color = "Gray") {
    Write-Host $Text -ForegroundColor $Color
    if ($writer) {
        $writer.WriteLine($Text)
    }
}

function Get-Header($Response, [string]$Name) {
    $values = $null
    if ($Response.Headers.TryGetValues($Name, [ref]$values)) {
        return @($values)[0]
    }
    return "-"
}

function Format-Shares($Counts, [int]$Total) {
    $parts = foreach ($key in ($Counts.Keys | Sort-Object)) {
        "{0}: {1} ({2:N0} %)" -f $key, $Counts[$key], (100.0 * $Counts[$key] / $Total)
    }
    return ($parts -join ", ")
}

$colors = @{ "1.0.0" = "Cyan"; "2.0.0" = "Yellow" }
$windowCounts = @{}
$totalCounts = @{}
$sent = 0

$headerText = if ($Group) { ", x-user-group: $Group" } else { "" }
Write-Line ("=== {0:yyyy-MM-dd HH:mm:ss} | GET {1}{2}" -f (Get-Date), $url, $headerText) "Green"
Write-Line "время        | код | версия | под" "Green"

try {
    while ($Count -eq 0 -or $sent -lt $Count) {
        $now = Get-Date
        try {
            $response = $client.GetAsync($url).GetAwaiter().GetResult()
            $status = [int]$response.StatusCode
            $version = Get-Header $response "X-App-Version"
            $pod = Get-Header $response "X-Pod"
            $response.Dispose()
        } catch {
            $status = 0
            $version = "-"
            $pod = $_.Exception.GetBaseException().Message
        }
        $sent++
        $key = if ($status -ge 200 -and $status -lt 400) { $version } else { "ошибка" }
        $windowCounts[$key] = 1 + [int]$windowCounts[$key]
        $totalCounts[$key] = 1 + [int]$totalCounts[$key]

        $color = if ($key -eq "ошибка") { "Red" } elseif ($colors.ContainsKey($version)) { $colors[$version] } else { "Gray" }
        Write-Line ("{0:HH:mm:ss.fff} | {1,3} | {2,-6} | {3}" -f $now, $status, $version, $pod) $color

        if ($sent % $Window -eq 0) {
            Write-Line ("--- последние {0}: {1}" -f $Window, (Format-Shares $windowCounts $Window)) "Green"
            $windowCounts = @{}
        }
        Start-Sleep -Milliseconds ([int]($Interval * 1000))
    }
} finally {
    if ($sent -gt 0) {
        Write-Line ("=== всего {0}: {1}" -f $sent, (Format-Shares $totalCounts $sent)) "Green"
    }
    if ($writer) {
        $writer.Dispose()
    }
    $client.Dispose()
}

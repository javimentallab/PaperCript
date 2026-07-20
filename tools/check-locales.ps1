# Checks that every language lines up with values/strings.xml (the reference):
# no missing or extra keys, and the same format specifiers in each string.
#
# The second part is what really matters: a %1$s translated as %1$d compiles
# happily and blows up while formatting, in front of the user.
#
#   pwsh tools/check-locales.ps1
#
# Exits with code 1 if any language does not line up.

$ErrorActionPreference = 'Stop'
$res = Join-Path (Split-Path $PSScriptRoot -Parent) 'app\src\main\res'

function Get-Keys($path) {
    $raw = Get-Content $path -Raw
    $strings = [regex]::Matches($raw, '<string name="([a-z_]+)"') | ForEach-Object { 's:' + $_.Groups[1].Value }
    $plurals = [regex]::Matches($raw, '<plurals name="([a-z_]+)"') | ForEach-Object { 'p:' + $_.Groups[1].Value }
    return ($strings + $plurals) | Sort-Object -Unique
}

function Get-Specs($path) {
    # Count format specifiers per key, which is where an error most easily slips in
    $raw = Get-Content $path -Raw
    $out = @{}
    foreach ($m in [regex]::Matches($raw, '(?s)<string name="([a-z_]+)">(.*?)</string>')) {
        $out[$m.Groups[1].Value] = ([regex]::Matches($m.Groups[2].Value, '%\d\$[sd]') | ForEach-Object { $_.Value } | Sort-Object) -join ','
    }
    return $out
}

$base = Join-Path $res 'values\strings.xml'
$baseKeys = Get-Keys $base
$baseSpecs = Get-Specs $base

Write-Output ("reference (en): {0} keys" -f $baseKeys.Count)
Write-Output ''

$problems = 0
foreach ($dir in Get-ChildItem $res -Directory | Where-Object { $_.Name -match '^values-' }) {
    $file = Join-Path $dir.FullName 'strings.xml'
    if (-not (Test-Path $file)) { continue }

    $keys = Get-Keys $file
    $specs = Get-Specs $file
    $lang = $dir.Name -replace '^values-', ''
    $errs = @()

    $diff = Compare-Object $baseKeys $keys
    if ($diff) {
        foreach ($d in $diff) {
            $side = if ($d.SideIndicator -eq '<=') { 'MISSING' } else { 'EXTRA' }
            $errs += "$side $($d.InputObject)"
        }
    }

    foreach ($k in $baseSpecs.Keys) {
        if ($specs.ContainsKey($k) -and $specs[$k] -ne $baseSpecs[$k]) {
            $errs += "format mismatch in '$k': expected [$($baseSpecs[$k])] but found [$($specs[$k])]"
        }
    }

    if ($errs.Count -eq 0) {
        Write-Output ("  {0,-6} OK  {1} keys" -f $lang, $keys.Count)
    } else {
        $problems++
        Write-Output ("  {0,-6} PROBLEMS:" -f $lang)
        $errs | ForEach-Object { Write-Output "         $_" }
    }
}

Write-Output ''
if ($problems -eq 0) {
    Write-Output 'All languages line up with the reference.'
} else {
    Write-Output "$problems language(s) with problems."
    exit 1
}

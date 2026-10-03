param([string]$BaseUrl = 'http://127.0.0.1:8081')
$ErrorActionPreference = 'Stop'

$ready = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    try {
        $health = Invoke-RestMethod "$BaseUrl/actuator/health" -TimeoutSec 2
        if ($health.status -eq 'UP') { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 1
}
if (-not $ready) { throw 'WienScore did not become healthy within 30 startup checks.' }

$page = Invoke-WebRequest "$BaseUrl/" -TimeoutSec 10
if ($page.Content -notmatch '<app-root') { throw 'The built Angular application is not being served.' }
$body = '{"address":"Hoher Markt 1, 1010 Wien","profile":"STUDENT"}'
$first = Invoke-RestMethod "$BaseUrl/api/assessments" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 100
$second = Invoke-RestMethod "$BaseUrl/api/assessments" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 100
if ($first.status -ne 'OK') { throw "Live assessment unavailable: $($first.message)" }
if ($first.grade -notin 1..5 -or $first.factors.Count -ne 4) { throw 'Missing grade or mandatory factors.' }
if ($first.score -ne $second.score -or $first.grade -ne $second.grade) { throw 'Repeated results differ.' }
if (($first.sources.sha256 -join ',') -ne ($second.sources.sha256 -join ',')) { throw 'Dataset snapshots changed between requests.' }
$contributions = ($first.factors | Measure-Object -Property contribution -Sum).Sum
if ([Math]::Abs($contributions - $first.score) -gt 0.0000001) { throw 'Contributions do not sum to the score.' }

$seniorBody = '{"address":"Hoher Markt 1, 1010 Vienna","profile":"SENIOR"}'
$senior = Invoke-RestMethod "$BaseUrl/api/assessments" -Method Post -ContentType 'application/json' -Body $seniorBody -TimeoutSec 100
$seniorRepeat = Invoke-RestMethod "$BaseUrl/api/assessments" -Method Post -ContentType 'application/json' -Body $seniorBody -TimeoutSec 100
if ($senior.status -ne 'OK' -or $senior.profile -ne 'Senior' -or $senior.modelVersion -ne 'neighbourhood-proximity-v3') { throw 'Senior profile assessment failed.' }
if ($senior.score -ne $seniorRepeat.score -or $senior.grade -ne $seniorRepeat.grade) { throw 'Senior repeat result differs.' }
if (($first.sources.sha256 -join ',') -ne ($senior.sources.sha256 -join ',')) { throw 'Profile comparison used different snapshots.' }
$seniorWeights = @(0.3125, 0.25, 0.375, 0.0625)
for ($index = 0; $index -lt 4; $index++) {
    if ($senior.factors[$index].weight -ne $seniorWeights[$index]) { throw 'Incorrect senior weights.' }
    if (($first.factors[$index].measures | ConvertTo-Json -Compress) -ne ($senior.factors[$index].measures | ConvertTo-Json -Compress)) { throw 'Profile selection changed distances.' }
}
if ([Math]::Abs(($senior.factors | Measure-Object -Property contribution -Sum).Sum - $senior.score) -gt 0.0000001) { throw 'Senior contributions do not sum to the score.' }

$missing = Invoke-RestMethod "$BaseUrl/api/assessments" -Method Post -ContentType 'application/json' -Body '{"address":"INNO2 nonexistent street 999999"}' -TimeoutSec 25
if ($missing.status -ne 'NO_MATCH' -or $null -ne $missing.grade) { throw 'No-match behavior failed.' }
[pscustomobject]@{
    Address = $first.location.label
    Grade = $first.grade
    Score = $first.score
    SeniorGrade = $senior.grade
    SeniorScore = $senior.score
    ProfilesUseSameMeasurements = $true
    Factors = $first.factors.Count
    SameSnapshotsAndScore = $true
    NoMatchWithholdsGrade = $true
}

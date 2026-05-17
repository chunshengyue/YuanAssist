param(
    [string]$OutDir = "data/yuan_bwiki"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Web

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$baseUrl = "https://wiki.biligame.com/yuan"
$calculatorUrl = "$baseUrl/index.php?title=%E9%9D%A2%E6%9D%BF%E8%AE%A1%E7%AE%97%E5%99%A8"
$apiUrl = "$baseUrl/api.php"
$headers = @{
    "User-Agent" = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
}

function U {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [object[]]$CodePoints
    )
    $builder = New-Object System.Text.StringBuilder
    foreach ($codePoint in $CodePoints) {
        if ($codePoint -is [System.Array]) {
            foreach ($nestedCodePoint in $codePoint) {
                [void]$builder.Append([char]([int]$nestedCodePoint))
            }
        } else {
            [void]$builder.Append([char]([int]$codePoint))
        }
    }
    return $builder.ToString()
}

$titleCalculator = U 0x9762 0x677F 0x8BA1 0x7B97 0x5668
$titleWidgetCalculator = "Widget:" + $titleCalculator
$categoryRole = "Category:" + (U 0x5BC6 0x63A2)
$roleFieldNames = @(
    (U 0x59D3 0x540D),
    (U 0x54C1 0x8D28),
    (U 0x90E8 0x95E8),
    (U 0x5C5E 0x6027),
    (U 0x804C 0x4E1A),
    (U 0x6807 0x7B7E) + "1",
    (U 0x6807 0x7B7E) + "2",
    (U 0x51FA 0x8EAB),
    (U 0x559C 0x597D),
    (U 0x6027 0x683C),
    (U 0x5C5E 0x6027) + "1",
    (U 0x5C5E 0x6027) + "2",
    (U 0x5C5E 0x6027) + "3",
    (U 0x666E 0x653B),
    (U 0x6280 0x80FD),
    (U 0x6D88 0x8017 0x80FD 0x91CF),
    (U 0x961F 0x957F 0x6280 0x80FD),
    (U 0x5929 0x8D4B) + "1",
    (U 0x5929 0x8D4B) + "2",
    (U 0x5929 0x8D4B) + "3",
    (U 0x5929 0x8D4B) + "4",
    (U 0x5929 0x8D4B 0x89E3 0x9501) + "4"
)

function Ensure-Dir([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Write-Utf8([string]$Path, [string]$Text) {
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    [System.IO.File]::WriteAllText($fullPath, $Text, $utf8NoBom)
}

function Invoke-Text([string]$Uri) {
    if (-not [System.Uri]::IsWellFormedUriString($Uri, [System.UriKind]::Absolute)) {
        throw "Invalid URI: $Uri"
    }
    $lastError = $null
    $tempFile = [System.IO.Path]::GetTempFileName()
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            & curl.exe -L --compressed `
                -A $headers["User-Agent"] `
                -H "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" `
                -H "Accept-Language: zh-CN,zh;q=0.9" `
                -H "Referer: https://wiki.biligame.com/yuan/" `
                -o $tempFile `
                $Uri | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw "curl exited with code $LASTEXITCODE"
            }
            $text = [System.IO.File]::ReadAllText($tempFile, [System.Text.Encoding]::UTF8)
            if ($text -match "EdgeOne|请求已被站点的安全策略拦截|Access Restricted") {
                throw "blocked by EdgeOne"
            }
            return $text
        } catch {
            $lastError = $_
            Start-Sleep -Seconds $attempt
        }
    }
    Remove-Item -LiteralPath $tempFile -ErrorAction SilentlyContinue
    throw "Request failed: $Uri`n$($lastError.Exception.Message)"
}

function ConvertTo-PrettyJson($Value, [int]$Depth = 32) {
    return ($Value | ConvertTo-Json -Depth $Depth)
}

function Get-HiddenJson($Html, [string]$Id) {
    $pattern = '(?s)<div style="display:none;" id="' + [regex]::Escape($Id) + '">(.*?)</div>'
    $match = [regex]::Match($Html, $pattern)
    if (-not $match.Success) {
        throw "Cannot find hidden JSON block: $Id"
    }
    $json = [System.Web.HttpUtility]::HtmlDecode($match.Groups[1].Value.Trim())
    return $json | ConvertFrom-Json
}

function Get-AllCategoryMembers([string]$CategoryTitle) {
    $members = @()
    $continue = $null
    do {
        $uri = "${apiUrl}?action=query&list=categorymembers&cmtitle=$([uri]::EscapeDataString($CategoryTitle))&cmlimit=500&format=json"
        if ($continue) {
            $uri += "&cmcontinue=$([uri]::EscapeDataString($continue))"
        }
        $json = Invoke-Text $uri | ConvertFrom-Json
        $members += @($json.query.categorymembers | Where-Object { $_.ns -eq 0 })
        $continue = $null
        if ($json.PSObject.Properties.Name -contains "continue") {
            $continue = $json.continue.cmcontinue
        }
    } while ($continue)
    return $members
}

function Get-RawPage([string]$Title) {
    $encodedTitle = [System.Web.HttpUtility]::UrlEncode($Title, [System.Text.Encoding]::UTF8)
    $uri = "$baseUrl/index.php?title=$encodedTitle&action=raw"
    return Invoke-Text $uri
}

function ConvertTo-SafeFileName([string]$Name) {
    $safe = $Name
    foreach ($char in [System.IO.Path]::GetInvalidFileNameChars()) {
        $safe = $safe.Replace([string]$char, "_")
    }
    return $safe
}

function Parse-RoleFields([string]$Title, [string]$RawText, [string]$RawFile) {
    $item = [ordered]@{
        title = $Title
        rawFile = $RawFile
    }

    foreach ($key in $roleFieldNames) {
        $pattern = '(?m)^\|' + [regex]::Escape($key) + '=(.*)$'
        $match = [regex]::Match($RawText, $pattern)
        if ($match.Success) {
            $item[$key] = $match.Groups[1].Value.Trim()
        }
    }

    return [pscustomobject]$item
}

Ensure-Dir $OutDir
Ensure-Dir (Join-Path $OutDir "calculator")
Ensure-Dir (Join-Path $OutDir "roles_raw")
Ensure-Dir (Join-Path $OutDir "widget")

$calculatorHtml = Invoke-Text $calculatorUrl
Write-Utf8 (Join-Path $OutDir "calculator/page.html") $calculatorHtml

$roleNumValue = Get-HiddenJson $calculatorHtml "RoleNumValue"
$starStoneValue = Get-HiddenJson $calculatorHtml "StarStoneValue"
$starStoneGroup = Get-HiddenJson $calculatorHtml "StarStoneGroup"

Write-Utf8 (Join-Path $OutDir "calculator/RoleNumValue.json") (ConvertTo-PrettyJson $roleNumValue)
Write-Utf8 (Join-Path $OutDir "calculator/StarStoneValue.json") (ConvertTo-PrettyJson $starStoneValue)
Write-Utf8 (Join-Path $OutDir "calculator/StarStoneGroup.json") (ConvertTo-PrettyJson $starStoneGroup)

$calculatorRaw = Get-RawPage $titleCalculator
Write-Utf8 (Join-Path $OutDir "calculator/page.wiki") $calculatorRaw

$widgetRaw = Get-RawPage $titleWidgetCalculator
Write-Utf8 (Join-Path $OutDir "widget/calculator.wiki") $widgetRaw

$roleMembers = Get-AllCategoryMembers $categoryRole
Write-Utf8 (Join-Path $OutDir "role_index.json") (ConvertTo-PrettyJson $roleMembers)

$parsedRoles = @()
foreach ($member in $roleMembers) {
    $title = [string]$member.title
    $safeName = ConvertTo-SafeFileName $title
    $rawFile = "roles_raw/$safeName.wiki"
    $rawPath = Join-Path $OutDir $rawFile
    $rawText = Get-RawPage $title
    Write-Utf8 $rawPath $rawText
    $parsedRoles += Parse-RoleFields $title $rawText $rawFile
}

Write-Utf8 (Join-Path $OutDir "roles_basic.json") (ConvertTo-PrettyJson $parsedRoles)

$manifest = [ordered]@{
    source = $calculatorUrl
    crawledAt = (Get-Date).ToString("o")
    counts = [ordered]@{
        roleNumValue = @($roleNumValue).Count
        starStoneValue = @($starStoneValue).Count
        rolePages = @($roleMembers).Count
    }
    files = @(
        "calculator/page.html",
        "calculator/page.wiki",
        "calculator/RoleNumValue.json",
        "calculator/StarStoneValue.json",
        "calculator/StarStoneGroup.json",
        "widget/calculator.wiki",
        "role_index.json",
        "roles_basic.json",
        "roles_raw/*.wiki"
    )
}
Write-Utf8 (Join-Path $OutDir "manifest.json") (ConvertTo-PrettyJson ([pscustomobject]$manifest))

Write-Output "Done."
Write-Output "Output: $([System.IO.Path]::GetFullPath($OutDir))"
Write-Output "RoleNumValue: $(@($roleNumValue).Count)"
Write-Output "StarStoneValue: $(@($starStoneValue).Count)"
Write-Output "Role pages: $(@($roleMembers).Count)"

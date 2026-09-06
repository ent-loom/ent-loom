[CmdletBinding()]
param(
    [string]$MavenRepository = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$composeProject = "ent-loom-customer-profile"
$envPath = Join-Path $PSScriptRoot "..\.env"
$envExamplePath = Join-Path $PSScriptRoot "..\.env.example"
$logDirectory = Join-Path $PSScriptRoot "..\target\verification-logs"
$appLogPath = Join-Path $logDirectory "application.log"
$appErrorLogPath = Join-Path $logDirectory "application-error.log"
$composeLogPath = Join-Path $logDirectory "compose.log"
$appProcess = $null
$composeStarted = $false

function Read-DotEnv([string]$path) {
    $values = @{}
    if (-not (Test-Path -LiteralPath $path)) {
        return $values
    }
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            $values[$Matches[1]] = $Matches[2].Trim('"', "'")
        }
    }
    return $values
}

function Get-EnvValue([hashtable]$values, [string]$name, [string]$defaultValue) {
    $environmentValue = [Environment]::GetEnvironmentVariable($name)
    if ($environmentValue) {
        return $environmentValue
    }
    if ($values.ContainsKey($name) -and $values[$name]) {
        return $values[$name]
    }
    return $defaultValue
}

function Invoke-Checked([string]$file, [string[]]$arguments) {
    & $file @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed ($LASTEXITCODE): $file $($arguments -join ' ')"
    }
}

function Resolve-MavenCommand() {
    $maven = Get-Command mvn -ErrorAction SilentlyContinue
    if ($maven) {
        return $maven.Source
    }
    $wrapper = Join-Path $PSScriptRoot "..\..\..\mvnw.cmd"
    if (Test-Path -LiteralPath $wrapper) {
        return (Resolve-Path -LiteralPath $wrapper).Path
    }
    throw "Maven was not found. Install Maven 3.9+ or run this script from the ent-loom workspace with its Maven Wrapper."
}

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) {
        throw $message
    }
}

$dotenv = Read-DotEnv ($(if (Test-Path -LiteralPath $envPath) { $envPath } else { $envExamplePath }))
$mysqlDatabase = Get-EnvValue $dotenv "MYSQL_DATABASE" "customer_profile"
$mysqlUser = Get-EnvValue $dotenv "MYSQL_USER" "customer_profile"
$mysqlPassword = Get-EnvValue $dotenv "MYSQL_PASSWORD" "customer_profile_dev"
$mysqlPort = Get-EnvValue $dotenv "MYSQL_PORT" "3307"
$appPort = Get-EnvValue $dotenv "APP_PORT" "8080"
$customerId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null

try {
    Push-Location (Join-Path $PSScriptRoot "..")
    if (-not $SkipBuild) {
        $mavenCommand = Resolve-MavenCommand
        $buildArguments = @("-q", "-DskipTests", "package")
        if ($MavenRepository) {
            $buildArguments = @("-q", "-Dmaven.repo.local=$MavenRepository", "-DskipTests", "package")
        }
        Invoke-Checked $mavenCommand $buildArguments
    }

    $composeFile = Join-Path (Get-Location) "compose.yaml"
    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
    Assert-True ($null -ne $dockerCommand) "Docker CLI was not found. Install Docker Desktop with Docker Compose v2."
    $composeStarted = $true
    $composeArguments = @("--project-name", $composeProject, "--env-file", $(if (Test-Path -LiteralPath $envPath) { $envPath } else { $envExamplePath }), "-f", $composeFile, "up", "-d", "mysql")
    Invoke-Checked "docker" (@("compose") + $composeArguments)

    $containerId = (& docker compose --project-name $composeProject --env-file $(if (Test-Path -LiteralPath $envPath) { $envPath } else { $envExamplePath }) -f $composeFile ps -q mysql).Trim()
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $health = (& docker inspect --format '{{.State.Health.Status}}' $containerId 2>$null).Trim()
        if ($health -eq "healthy") {
            break
        }
        if ($health -eq "unhealthy") {
            throw "MySQL container became unhealthy"
        }
        Start-Sleep -Seconds 2
    }
    Assert-True (($health -eq "healthy")) "MySQL did not become healthy"

    $jar = Join-Path (Get-Location) "target\customer-profile-spring-boot-0.1.0-SNAPSHOT.jar"
    Assert-True (Test-Path -LiteralPath $jar) "Application jar not found: $jar"
    $databaseUrl = "jdbc:mysql://localhost:$mysqlPort/$mysqlDatabase?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
    $appProcess = Start-Process -FilePath "java" -ArgumentList @(
        "-jar", $jar,
        "--spring.profiles.active=verify",
        "--server.port=$appPort",
        "--spring.datasource.url=$databaseUrl",
        "--spring.datasource.username=$mysqlUser",
        "--spring.datasource.password=$mysqlPassword"
    ) -RedirectStandardOutput $appLogPath -RedirectStandardError $appErrorLogPath -PassThru

    $healthUrl = "http://localhost:$appPort/actuator/health"
    $appReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $healthResponse = Invoke-RestMethod -Uri $healthUrl -Method Get
            if ($healthResponse.status -eq "UP") {
                $appReady = $true
                break
            }
        } catch {
            if ($appProcess.HasExited) {
                throw "Application stopped before becoming ready. See $appLogPath"
            }
        }
        Start-Sleep -Seconds 2
    }
    Assert-True $appReady "Application did not become healthy. See $appLogPath"

    $headers = @{ "Content-Type" = "application/json" }
    $createBody = @{
        options = @{ requestId = "customer-profile-create-$customerId" }
        payload = @{ id = [long]$customerId; displayName = "Ada Lovelace"; email = "ada@example.com" }
    } | ConvertTo-Json -Depth 5
    $createResponse = Invoke-RestMethod -Uri "http://localhost:$appPort/api/ent-crud/customer_profile/create" -Method Post -Headers $headers -Body $createBody
    Assert-True ($createResponse.success -eq $true) "CREATE failed: $($createResponse | ConvertTo-Json -Depth 8)"
    $createdId = [long]$createResponse.data.id
    Assert-True ($createdId -eq $customerId) "CREATE returned unexpected ID: $createdId"

    $detailBody = @{
        options = @{
            requestId = "customer-profile-detail-$customerId"
            filterMap = @{ id = @{ op = "EQ"; value = $createdId } }
        }
    } | ConvertTo-Json -Depth 8
    $detailResponse = Invoke-RestMethod -Uri "http://localhost:$appPort/api/ent-crud/customer_profile/detail" -Method Post -Headers $headers -Body $detailBody
    Assert-True ($detailResponse.success -eq $true) "DETAIL failed: $($detailResponse | ConvertTo-Json -Depth 8)"
    Assert-True ([long]$detailResponse.data.item.id -eq $createdId) "DETAIL returned unexpected ID"
    Assert-True ($detailResponse.data.item.display_name -eq "Ada Lovelace") "DETAIL returned unexpected display_name"
    Assert-True ($detailResponse.data.item.email -eq "ada@example.com") "DETAIL returned unexpected email"

    $sql = "select id, display_name, email from $mysqlDatabase.customer_profile where id = $createdId"
    $sqlOutput = & docker compose --project-name $composeProject --env-file $(if (Test-Path -LiteralPath $envPath) { $envPath } else { $envExamplePath }) -f $composeFile exec -T mysql mysql -u$mysqlUser -p$mysqlPassword -N -B $mysqlDatabase -e $sql
    if ($LASTEXITCODE -ne 0) {
        throw "SQL verification failed"
    }
    $columns = $sqlOutput.Trim() -split "`t"
    Assert-True ($columns.Length -eq 3) "SQL verification returned unexpected columns: $sqlOutput"
    Assert-True ([long]$columns[0] -eq $createdId) "SQL returned unexpected ID"
    Assert-True ($columns[1] -eq "Ada Lovelace") "SQL returned unexpected display_name"
    Assert-True ($columns[2] -eq "ada@example.com") "SQL returned unexpected email"
    Write-Host "Verification passed: CREATE -> DETAIL -> SQL (id=$createdId)"
} catch {
    Write-Error $_
    if ($composeStarted) {
        & docker compose --project-name $composeProject --env-file $(if (Test-Path -LiteralPath $envPath) { $envPath } else { $envExamplePath }) -f (Join-Path (Join-Path $PSScriptRoot "..") "compose.yaml") logs --no-color mysql 2>&1 | Out-File -FilePath $composeLogPath -Encoding utf8
    }
    throw
} finally {
    if ($appProcess -and -not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -Force
    }
    if ($composeStarted) {
        & docker compose --project-name $composeProject --env-file $(if (Test-Path -LiteralPath $envPath) { $envPath } else { $envExamplePath }) -f (Join-Path (Join-Path $PSScriptRoot "..") "compose.yaml") down
    }
    Pop-Location
}

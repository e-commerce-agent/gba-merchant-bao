param([Parameter(Mandatory=$true)][string]$BaseUrl)
$ErrorActionPreference='Stop'
$base=[Uri]$BaseUrl
if (-not $base.IsLoopback -or $base.Scheme -ne 'http') { throw 'Use an isolated loopback development backend.' }
$BaseUrl=$BaseUrl.TrimEnd('/')
function Call-Auth([string]$Method,[string]$Path,$Body,[string]$Token,[int]$Expected=200) {
    $rid=[Guid]::NewGuid().ToString()
    $headers=@{'X-Request-Id'=$rid}
    if ($Token) { $headers.Authorization='Bearer '+$Token }
    $requestOptions=@{Uri=$BaseUrl+$Path;Method=$Method;Headers=$headers;UseBasicParsing=$true;ContentType='application/json'}
    if ($null -ne $Body) { $requestOptions.Body=ConvertTo-Json -InputObject $Body -Compress }
    try { $response=Invoke-WebRequest @requestOptions; $status=[int]$response.StatusCode; $content=$response.Content }
    catch {
        $response=$_.Exception.Response
        if ($null -eq $response) { throw 'Backend connection failed.' }
        $status=[int]$response.StatusCode
        $content=$_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($content)) {
            $reader=New-Object System.IO.StreamReader($response.GetResponseStream())
            try { $content=$reader.ReadToEnd() } finally { $reader.Dispose() }
        }
    }
    if ($status -ne $Expected) { throw "Unexpected HTTP status at $Path : $status (expected $Expected)." }
    $result=ConvertFrom-Json -InputObject $content
    if ($result.requestId -ne $rid -or $response.Headers['X-Request-Id'] -ne $rid -or $response.Headers['Cache-Control'] -notlike '*no-store*') { throw "Correlation/cache contract failed at $Path (HTTP $status)." }
    return $result.data
}
# Public development-only password. Never copy this account or password into production.
$password='DevOnly-Auth-2026!'
$staff=Call-Auth POST '/api/v1/auth/staff/login' @{merchantCode='M_3C_DEMO';identifier='demo_staff';password=$password} $null
$staffPhone=Call-Auth POST '/api/v1/auth/staff/login' @{merchantCode='M_3C_DEMO';identifier='13800000301';password=$password} $null
if ($staff.principal.accountId -ne $staffPhone.principal.accountId) { throw 'Staff identifiers differ.' }
$otherStaff=Call-Auth POST '/api/v1/auth/staff/login' @{merchantCode='M_ISOLATION_TEST';identifier='demo_staff';password=$password} $null
Write-Output 'PASS staff username/phone and two tenants'
$suffix=[Guid]::NewGuid().ToString('N').Substring(0,10)
$phone='138'+(Get-Random -Minimum 10000000 -Maximum 99999999)
$sms=Call-Auth POST '/api/v1/stores/M_3C_DEMO/auth/sms-codes' @{phone=$phone} $null
if ($sms.debugCode -notmatch '^\d{6}$') { throw 'Explicit dev mock/debug configuration is required.' }
$consumer=Call-Auth POST '/api/v1/stores/M_3C_DEMO/auth/consumer/login' @{phone=$phone;code=$sms.debugCode} $null
$null=Call-Auth POST '/api/v1/stores/M_3C_DEMO/auth/consumer/login' @{phone=$phone;code=$sms.debugCode} $null 400
$me=Call-Auth GET '/api/v1/auth/me' $null $consumer.accessToken
if ($me.accountId -ne $consumer.principal.accountId -or $me.role -ne 'CONSUMER') { throw 'Consumer principal differs.' }
$null=Call-Auth GET ('/api/v1/consumer-accounts/'+$me.accountId) $null $consumer.accessToken
$null=Call-Auth GET '/api/v1/consumer-accounts/2100000000000202' $null $consumer.accessToken 404
$null=Call-Auth GET '/api/v1/consumer-accounts/2100000000000203' $null $consumer.accessToken 404
$null=Call-Auth POST '/api/v1/staff-accounts' @{username='rejected'} $consumer.accessToken 403
Write-Output 'PASS mock SMS, automatic account, replay, me and resource ownership'
$created=Call-Auth POST '/api/v1/staff-accounts' @{username=('manual_'+$suffix);phone=('139'+(Get-Random -Minimum 10000000 -Maximum 99999999));displayName='Manual';password='ManualOnly-2026!'} $staff.accessToken 201
$null=Call-Auth GET ('/api/v1/staff-accounts/'+$created.accountId) $null $staff.accessToken
$null=Call-Auth GET ('/api/v1/staff-accounts/'+$created.accountId) $null $otherStaff.accessToken 404
$null=Call-Auth POST '/api/v1/auth/staff/login' @{merchantCode='M_3C_DEMO';identifier=$created.username;password='ManualOnly-2026!'} $null
Write-Output 'PASS controlled staff creation and tenant scope'
$requestId=[Guid]::NewGuid().ToString()
$refreshBody=@{refreshToken=$consumer.refreshToken;refreshRequestId=$requestId}
$rotated=Call-Auth POST '/api/v1/auth/refresh' $refreshBody 'invalid-access'
$retry=Call-Auth POST '/api/v1/auth/refresh' $refreshBody $null
if ($rotated.accessToken -ne $retry.accessToken -or $rotated.refreshToken -ne $retry.refreshToken -or $consumer.refreshExpiresAt -ne $rotated.refreshExpiresAt) { throw 'Refresh retry/absolute contract failed.' }
$null=Call-Auth POST '/api/v1/auth/logout' @{refreshToken=$consumer.refreshToken} 'invalid-access'
$null=Call-Auth POST '/api/v1/auth/logout' @{refreshToken=$rotated.refreshToken} $null
$null=Call-Auth GET '/api/v1/auth/me' $null $rotated.accessToken 401
$null=Call-Auth POST '/api/v1/auth/refresh' @{refreshToken=$rotated.refreshToken;refreshRequestId=[Guid]::NewGuid().ToString()} $null 401
$null=Call-Auth POST '/api/v1/auth/logout' @{} $staff.accessToken
$null=Call-Auth GET '/api/v1/auth/me' $null $staffPhone.accessToken
$null=Call-Auth GET '/api/v1/auth/me' $null $otherStaff.accessToken
Write-Output 'PASS encrypted retry, historical/idempotent logout and independent devices/tenants'
Write-Output 'PASS backend manual verification complete (no credentials printed)'

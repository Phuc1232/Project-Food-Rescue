# Windows setup form. Credentials are entered locally, never passed on the command line.
param([ValidateSet('Gmail', 'Resend')][string]$Provider = 'Gmail')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()
$codeDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envFile = Join-Path $codeDirectory '.env'
if (!(Test-Path -LiteralPath $envFile)) { throw 'Create CODE/.env from .env.example first.' }
& git -C $codeDirectory check-ignore --quiet -- $envFile
if ($LASTEXITCODE -ne 0) { throw 'CODE/.env must be ignored by Git before storing credentials.' }
$script:saved = $false
$form = New-Object Windows.Forms.Form
$form.Text = 'FoodRescue - Cấu hình email thật'
$form.ClientSize = New-Object Drawing.Size(580, 475)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.Font = New-Object Drawing.Font('Segoe UI', 10)
$form.TopMost = $true

function Add-Label([string]$Text, [int]$Top, [int]$Height = 28) {
    $label = New-Object Windows.Forms.Label
    $label.Text = $Text
    $label.Location = New-Object Drawing.Point(24, $Top)
    $label.Size = New-Object Drawing.Size(530, $Height)
    $form.Controls.Add($label)
}
Add-Label "Gửi email xác minh qua $Provider" 20 32
Add-Label 'Thông tin chỉ lưu vào CODE/.env trên máy này, không đưa lên Git hoặc chat.' 60 45
if ($Provider -eq 'Gmail') {
    Add-Label 'Bật Xác minh 2 bước, rồi tạo Mật khẩu ứng dụng tên FoodRescue. Không nhập mật khẩu đăng nhập Gmail.' 108 55
} else {
    Add-Label 'Thêm và xác minh tên miền trên Resend. Tạo API key chỉ có quyền gửi thư cho tên miền đó.' 108 55
}
$link = New-Object Windows.Forms.LinkLabel
$link.Text = if ($Provider -eq 'Gmail') { 'Mở trang tạo Mật khẩu ứng dụng Google' } else { 'Mở trang cấu hình Resend' }
$link.Location = New-Object Drawing.Point(24, 166)
$link.Size = New-Object Drawing.Size(530, 28)
$link.Add_LinkClicked({
    $url = if ($Provider -eq 'Gmail') { 'https://myaccount.google.com/apppasswords' } else { 'https://resend.com/domains' }
    [Diagnostics.Process]::Start((New-Object Diagnostics.ProcessStartInfo -Property @{FileName=$url; UseShellExecute=$true})) | Out-Null
})
$form.Controls.Add($link)
Add-Label 'Địa chỉ email gửi thư của dự án' 204
$email = New-Object Windows.Forms.TextBox
$email.Location = New-Object Drawing.Point(24, 234)
$email.Size = New-Object Drawing.Size(530, 30)
$form.Controls.Add($email)
Add-Label $(if ($Provider -eq 'Gmail') { 'Mật khẩu ứng dụng (16 ký tự)' } else { 'Resend API key' }) 281
$credential = New-Object Windows.Forms.TextBox
$credential.Location = New-Object Drawing.Point(24, 311)
$credential.Size = New-Object Drawing.Size(530, 30)
$credential.UseSystemPasswordChar = $true
$form.Controls.Add($credential)
$status = New-Object Windows.Forms.Label
$status.Location = New-Object Drawing.Point(24, 351)
$status.Size = New-Object Drawing.Size(530, 49)
$status.ForeColor = [Drawing.Color]::DarkRed
$form.Controls.Add($status)
$save = New-Object Windows.Forms.Button
$save.Text = 'Lưu cấu hình'
$save.Location = New-Object Drawing.Point(390, 414)
$save.Size = New-Object Drawing.Size(164, 36)
$save.Add_Click({
    $secret = $null
    try {
        $senderAddress = $email.Text.Trim().ToLowerInvariant()
        try { $parsed = New-Object Net.Mail.MailAddress($senderAddress) } catch { throw 'Email chưa hợp lệ.' }
        if ($parsed.Address -ne $senderAddress -or $senderAddress -notmatch '^[^\s<>]+@[^\s<>]+\.[^\s<>]+$') { throw 'Email chưa hợp lệ.' }
        $secret = $credential.Text.Trim()
        if ($Provider -eq 'Gmail') {
            $secret = $secret -replace '\s', ''
            if ($secret -notmatch '^[a-zA-Z]{16}$') { throw 'Cần Mật khẩu ứng dụng Google gồm 16 ký tự, không phải mật khẩu Gmail.' }
        } elseif ($secret.Length -lt 16) { throw 'API key chưa hợp lệ.' }
        if ($secret.Contains("'") -or $secret.Contains("`r") -or $secret.Contains("`n") -or $senderAddress.Contains("'")) { throw 'Giá trị cấu hình chứa ký tự không hỗ trợ.' }
        $settings = [ordered]@{
            MAIL_MODE = 'smtp'
            MAIL_HOST = $(if ($Provider -eq 'Gmail') { 'smtp.gmail.com' } else { 'smtp.resend.com' })
            MAIL_PORT = '587'
            MAIL_USERNAME = $(if ($Provider -eq 'Gmail') { $senderAddress } else { 'resend' })
            MAIL_PASSWORD = $secret
            MAIL_FROM = "FoodRescue <$senderAddress>"
            MAIL_AUTH = 'true'
            MAIL_STARTTLS = 'true'
            MAIL_SSL = 'false'
            MAIL_TEST_CONNECTION = 'true'
            COMPOSE_PROFILES = ''
        }
        $existing = [IO.File]::ReadAllLines($envFile)
        $updated = New-Object 'Collections.Generic.List[string]'
        foreach ($line in $existing) {
            if ($line -match '^\s*([A-Z_]+)\s*=' -and $settings.Contains($Matches[1])) { continue }
            $updated.Add($line)
        }
        foreach ($entry in $settings.GetEnumerator()) { $updated.Add("$($entry.Key)='$($entry.Value)'") }
        [IO.File]::WriteAllLines($envFile, $updated, (New-Object Text.UTF8Encoding($false)))
        $credential.Clear()
        $script:saved = $true
        $form.Close()
    } catch {
        # Do not display exception messages from system operations that might include sensitive values.
        $status.Text = 'Chưa lưu được. Kiểm tra email, mật khẩu ứng dụng/API key và quyền ghi CODE/.env.'
    } finally { $secret = $null; $settings = $null; $updated = $null }
})
$form.Controls.Add($save)
$form.AcceptButton = $save
$form.Add_Shown({ $form.Activate(); $form.BringToFront() })
[void]$form.ShowDialog()
$credential.Clear()
$form.Dispose()
if ($script:saved) {
    Write-Host 'Đã lưu cấu hình email. Chưa gửi thư thử. Khởi động lại backend để kiểm tra xác thực SMTP.'
    exit 0
}
Write-Host 'Đã đóng cửa sổ. Cấu hình không thay đổi.'
exit 1

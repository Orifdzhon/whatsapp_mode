# WAFake — one-shot installer (Windows / PowerShell)
#
# Что делает:
#   1. собирает APK (или берёт готовый)
#   2. ставит на подключённый по ADB телефон
#   3. через root включает модуль в LSPosed и добавляет scope com.whatsapp
#   4. force-stop WhatsApp, чтобы хук перецепился
#   5. открывает модуль в LSPosed для финальной проверки
#
# Требования: adb в PATH, root (Magisk/KernelSU), LSPosed, включённая отладка по USB.

param(
    [string]$Apk = "",
    [switch]$NoBuild,
    [switch]$SkipLsposed
)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here
$pkg = "dev.kaku.wafake"
$wa = "com.whatsapp"

function Step($n, $t) { Write-Host "`n[$n] $t" -ForegroundColor Cyan }
function Ok($t) { Write-Host "    ok  $t" -ForegroundColor Green }
function Warn($t) { Write-Host "    !!  $t" -ForegroundColor Yellow }

Step 1 "ADB"
adb start-server | Out-Null
$devices = (adb devices | Select-String -Pattern "\tdevice$")
if (-not $devices) { throw "Телефон не найден. Включи отладку по USB и разреши ключ." }
Ok ("устройств: " + $devices.Count)

Step 2 "APK"
if (-not $Apk) {
    $Apk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
}
if (-not $NoBuild -and -not (Test-Path $Apk)) {
    $gradlew = Join-Path $root "gradlew.bat"
    if (Test-Path $gradlew) {
        Push-Location $root
        & $gradlew assembleRelease
        Pop-Location
    } else {
        Warn "gradlew.bat не найден — собери APK в Android Studio (Build > Build APK)"
    }
}
if (-not (Test-Path $Apk)) { throw "APK не найден: $Apk" }
Ok $Apk

Step 3 "Установка"
adb install -r -g $Apk | Out-Null
$ver = adb shell dumpsys package $pkg | Select-String "versionName"
Ok ("установлено " + $ver)

Step 4 "Root / LSPosed"
$rootOk = (adb shell "su -c id" 2>$null) -match "uid=0"
if (-not $rootOk) {
    Warn "root недоступен через adb shell su — пропускаю авто-включение"
    $SkipLsposed = $true
}

if (-not $SkipLsposed) {
    # LSPosed хранит конфиг в /data/adb/lspd/config/modules_config.db
    $script = @'
set -e
DB=/data/adb/lspd/config/modules_config.db
[ -f "$DB" ] || { echo "NO_DB"; exit 2; }
sqlite3 "$DB" ".tables" | grep -q modules || { echo "NO_TABLES"; exit 3; }
MID=$(sqlite3 "$DB" "SELECT mid FROM modules WHERE module_pkg_name='dev.kaku.wafake' LIMIT 1;")
[ -n "$MID" ] || { echo "NO_MODULE"; exit 4; }
sqlite3 "$DB" "UPDATE modules SET enabled=1 WHERE mid='$MID';"
sqlite3 "$DB" "INSERT OR IGNORE INTO scope(mid, app_pkg_name, user_id) VALUES('$MID','com.whatsapp',0);" 2>/dev/null || true
echo "MID=$MID"
sqlite3 "$DB" "SELECT module_pkg_name, enabled FROM modules WHERE mid='$MID';"
sqlite3 "$DB" "SELECT app_pkg_name FROM scope WHERE mid='$MID';"
'@
    $tmp = Join-Path $env:TEMP "wafake_lspd.sh"
    $script -replace "`r`n", "`n" | Set-Content -NoNewline -Encoding ASCII $tmp
    adb push $tmp /data/local/tmp/wafake_lspd.sh | Out-Null
    $out = adb shell "su -c 'sh /data/local/tmp/wafake_lspd.sh'" 2>&1
    Write-Host $out
    if ($out -match "MID=") { Ok "модуль включён, scope com.whatsapp выставлен" }
    else { Warn "авто-включение не удалось — включи модуль в LSPosed вручную (шаг 5)" }
    adb shell "su -c 'rm -f /data/local/tmp/wafake_lspd.sh'"
}

Step 5 "Перезапуск WhatsApp"
adb shell am force-stop $wa
Start-Sleep -Milliseconds 700
adb shell monkey -p $wa -c android.intent.category.LAUNCHER 1 | Out-Null
Ok "WhatsApp перезапущен"

Step 6 "Открыть модуль"
adb shell am start -n "$pkg/.MainActivity" | Out-Null
Ok "WAFake открыт на телефоне"

Write-Host @"

Дальше на телефоне:
  LSPosed -> Модули -> WA Fake -> включить -> scope: WhatsApp
  Затем: WAFake -> вкладка архива должна быть пустой, чат-тест описан в README.

Логи хука:
  adb logcat -s WAFake:V
"@ -ForegroundColor Gray

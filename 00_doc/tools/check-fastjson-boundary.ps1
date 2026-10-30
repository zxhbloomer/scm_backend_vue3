# fastjson2 边界巡检脚本（静态退化为可判定规则）
# 规则来源：00_doc/26-收尾清单9项方案与实施计划-2026-10-29.md §2.9
# 边界约定：fastjson2 仅限 DB JSON 文本 / 外部响应解析 / MQ 存量消息与生产端 / MyBatis TypeHandler；
#           禁止新增 HTTP 出入口（Controller 层）用法。
# 用法：pwsh -NoProfile -File 00_doc/tools/check-fastjson-boundary.ps1 [-Root <项目根>]

param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

# 已知豁免（历史存量，本轮只登记不修改）
$exempt = @(
    'SystemBaseController.java'          # 基类：文件上传/下载返回
    'AppBaseController.java'             # 基类：同上
    'SMenuSearchController.java'         # JSON.toJSON 决定出参（存量）
    'AiConversationController.java'      # MQ/AI 存量消息体
    'WorkflowController.java'            # @RequestBody 直接收 fastjson2 类型（存量）
    'AntJsonToBpmnController.java'       # BPMN 转换，入参就是 fastjson2 类型（存量）
    'ReactJsonToBpmnController.java'     # 同上
    'JsonToBpmnController.java'          # 同上
    'BApController.java'                 # 业务存量
    'BPoOrderController.java'
    'BArController.java'
    'BSoOrderController.java'
    'BpmProcessTemplatesController.java'
)

$files = Get-ChildItem -Path $Root -Recurse -Filter '*Controller.java' -File |
    Where-Object { $_.FullName -notmatch '\\target\\' -and $_.FullName -match '\\src\\main\\java\\' }

$hits = @()
foreach ($f in $files) {
    $lines = [IO.File]::ReadAllLines($f.FullName)
    for ($i = 0; $i -lt $lines.Length; $i++) {
        $l = $lines[$i]
        if ($l -match '^\s*//') { continue }
        if ($l -match 'com\.alibaba\.fastjson2') {
            $hits += [pscustomobject]@{
                File    = $f.Name
                Path    = $f.FullName.Replace($Root + '\', '')
                Line    = $i + 1
                Code    = $l.Trim()
                Exempt  = ($exempt -contains $f.Name)
            }
        }
    }
}

Write-Host ("扫描 Controller 文件 {0} 个，命中 fastjson2 {1} 处" -f $files.Count, $hits.Count) -ForegroundColor Cyan

$violations = @($hits | Where-Object { -not $_.Exempt })
$known      = @($hits | Where-Object { $_.Exempt })

if ($known.Count -gt 0) {
    Write-Host "`n[豁免] 已登记的存量（不在本轮治理范围）：" -ForegroundColor Yellow
    $known | Format-Table File, Line, Code -AutoSize | Out-String -Width 220 | Write-Host
}

if ($violations.Count -eq 0) {
    Write-Host "[通过] 无新增 Controller 层 fastjson2 用法。" -ForegroundColor Green
    exit 0
}

Write-Host "`n[告警] 发现未登记的 Controller 层 fastjson2 用法，需按边界约定整改或补进豁免表：" -ForegroundColor Red
$violations | Format-Table File, Line, Code, Path -AutoSize | Out-String -Width 220 | Write-Host
Write-Host "整改方向：HTTP 出入口改用 Jackson 3（MessageConverter 已是 CallbackMappingJackson2HttpMessageConverter）；" -ForegroundColor Red
Write-Host "需要保留时，先把文件加进本脚本 `$exempt 并在 00_doc/26-收尾清单9项方案与实施计划-2026-10-29.md §6 白名单登记理由。" -ForegroundColor Red
exit 1

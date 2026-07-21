' IVD Hidden Launcher — 无窗口启动命令（立即返回）
' 用法: cscript //nologo launch-hidden.vbs "command" [logfile]

If WScript.Arguments.Count = 0 Then
    WScript.Quit 1
End If

Dim cmd, logFile, shell
cmd = WScript.Arguments(0)

If WScript.Arguments.Count >= 2 Then
    logFile = WScript.Arguments(1)
    cmd = "cmd /c " & cmd & " > """ & logFile & """ 2>&1"
Else
    cmd = "cmd /c " & cmd
End If

Set shell = CreateObject("WScript.Shell")
' 0 = SW_HIDE, False = 立即返回不等待
shell.Run cmd, 0, False

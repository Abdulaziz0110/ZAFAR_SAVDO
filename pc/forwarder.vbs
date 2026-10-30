' IMSOFT chek uzatgichi: forwarder.py ni oynasiz ishga tushiradi.
' Yorlig'ini Win+R > shell:startup papkasiga qo'ying - kompyuter yoqilganda o'zi ishlaydi.
Set fso = CreateObject("Scripting.FileSystemObject")
dir = fso.GetParentFolderName(WScript.ScriptFullName)
Set sh = CreateObject("WScript.Shell")
sh.CurrentDirectory = dir
exe = "pythonw"
If sh.Run("cmd /c where pythonw >nul 2>&1", 0, True) <> 0 Then exe = "pyw"
sh.Run exe & " """ & dir & "\forwarder.py""", 0, False

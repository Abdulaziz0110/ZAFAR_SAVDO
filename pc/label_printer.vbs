' IMSOFT narx yorlig'i printeri: label_printer.py ni oynasiz ishga tushiradi.
' Yorlig'ini Win+R > shell:startup papkasiga qo'ying - kompyuter yoqilganda o'zi ishlaydi.
Set fso = CreateObject("Scripting.FileSystemObject")
dir = fso.GetParentFolderName(WScript.ScriptFullName)
Set sh = CreateObject("WScript.Shell")
sh.CurrentDirectory = dir
sh.Run "pythonw """ & dir & "\label_printer.py""", 0, False

@echo off
chcp 65001 >nul
echo Jarvis Masaustu baslatiliyor... (ilk seferde kutuphaneler kurulur)
py -3 -m pip install -r requirements.txt
py -3 main.py
pause

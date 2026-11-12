#!/bin/bash
# Petit pilotage de l'émulateur Waydroid via adb (QA visuelle de Mes Garanties).
ADB="${ADB:-192.168.240.112:5555}"
A="adb -s $ADB"

dump() {
  $A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  $A shell cat /sdcard/ui.xml 2>/dev/null
}

cas() {
  case "$1" in
    dump)
      dump | python3 -c "
import sys,re
xml=sys.stdin.read()
for m in re.finditer(r'<node[^>]*>', xml):
    n=m.group(0)
    t=re.search(r'text=\"([^\"]*)\"',n); d=re.search(r'content-desc=\"([^\"]*)\"',n)
    b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n)
    libelle=(t.group(1) if t else '') or (d.group(1) if d else '')
    if libelle.strip() and b:
        x=(int(b.group(1))+int(b.group(3)))//2; y=(int(b.group(2))+int(b.group(4)))//2
        print(f'{libelle}\t{x},{y}')
"
      ;;
    tap)
      # tap "<libellé>" : cherche le texte ou content-desc et tape au centre
      COORD=$(dump | python3 -c "
import sys,re
cible=sys.argv[1].lower()
xml=sys.stdin.read()
best=None
for m in re.finditer(r'<node[^>]*>', xml):
    n=m.group(0)
    t=re.search(r'text=\"([^\"]*)\"',n); d=re.search(r'content-desc=\"([^\"]*)\"',n)
    b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n)
    libelle=((t.group(1) if t else '') or (d.group(1) if d else '')).lower()
    if b and libelle.strip() and cible in libelle:
        x=(int(b.group(1))+int(b.group(3)))//2; y=(int(b.group(2))+int(b.group(4)))//2
        print(f'{x} {y}'); break
" "$2")
      if [ -n "$COORD" ]; then $A shell input tap $COORD; echo "tap $2 -> $COORD"; else echo "INTROUVABLE: $2"; fi
      ;;
    text)
      $A shell input text "$2"; echo "text $2"
      ;;
    shot)
      $A exec-out screencap -p > "$2"; echo "capture: $2"
      ;;
    key)
      $A shell input keyevent "$2"; echo "key $2"
      ;;
    *)
      echo "usage: ui.sh dump|tap <libelle>|text <texte>|shot <fichier>|key <code>"
      ;;
  esac
}

cas "$@"

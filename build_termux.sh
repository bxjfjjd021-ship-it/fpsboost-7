#!/data/data/com.termux/files/usr/bin/bash
# Сборка .jar прямо в Termux. Нужен интернет (первая сборка качает ~1 ГБ).
set -e
pkg update -y
pkg install -y openjdk-21 gradle
export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
cd "$(dirname "$0")"
if [ ! -f gradlew ]; then
  TMP="$(mktemp -d)"
  (cd "$TMP" && gradle wrapper --gradle-version 8.12)
  cp -r "$TMP/gradle" "$TMP/gradlew" .
  chmod +x gradlew
fi
./gradlew build --no-daemon
mkdir -p /sdcard/Download 2>/dev/null || true
cp build/libs/fpsmenu-*.jar /sdcard/Download/ 2>/dev/null \
  && echo "Готово: /sdcard/Download/$(ls build/libs | grep -v sources | head -1)" \
  || echo "Готово: build/libs/"

#!/usr/bin/env bash
# Installs the build toolchain into one directory, without root:
# Temurin JDK 17, Android command-line tools, platform, build tools and the NDK.
#
#   scripts/setup-toolchain.sh [dir]     (default: $HOME/android-toolchain)
#
# Then: source <dir>/env.sh
set -euo pipefail

DIR="${1:-$HOME/android-toolchain}"
JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz"
JDK_SHA256="8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711"
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip"
CMDLINE_SHA1="e025545c62a8e64c7559119566a569fb1dec5f60"
SDK_PACKAGES=("platforms;android-36" "build-tools;35.0.0" "platform-tools" "ndk;27.3.13750724")

mkdir -p "$DIR/dl"
cd "$DIR"

if [ ! -x jdk/bin/java ]; then
  curl -fL --retry 3 -o dl/jdk.tar.gz "$JDK_URL"
  echo "$JDK_SHA256  dl/jdk.tar.gz" | sha256sum -c -
  rm -rf jdk.tmp && mkdir jdk.tmp
  tar -xzf dl/jdk.tar.gz -C jdk.tmp --strip-components=1
  mv jdk.tmp jdk
fi

if [ ! -x sdk/cmdline-tools/latest/bin/sdkmanager ]; then
  curl -fL --retry 3 -o dl/cmdline-tools.zip "$CMDLINE_URL"
  echo "$CMDLINE_SHA1  dl/cmdline-tools.zip" | sha1sum -c -
  rm -rf sdk/cmdline-tools && mkdir -p sdk/cmdline-tools
  unzip -q dl/cmdline-tools.zip -d sdk/cmdline-tools
  mv sdk/cmdline-tools/cmdline-tools sdk/cmdline-tools/latest
fi

cat > env.sh <<EOF
export JAVA_HOME="$DIR/jdk"
export ANDROID_HOME="$DIR/sdk"
export ANDROID_SDK_ROOT="$DIR/sdk"
export ANDROID_NDK_HOME="$DIR/sdk/ndk/27.3.13750724"
export PATH="$DIR/jdk/bin:$DIR/sdk/cmdline-tools/latest/bin:$DIR/sdk/platform-tools:\$PATH"
EOF
# shellcheck disable=SC1091
. ./env.sh

sdkmanager --install "${SDK_PACKAGES[@]}"
echo "toolchain ready: source $DIR/env.sh"

#!/system/bin/sh
# Runs a command inside the app's Debian as faked root, with the proot command line the app uses.
# For `adb shell run-as tech.nouverse.nouride.android files/nr.sh <command...>`. The library path holds
# "==", which env would take for an assignment: proot is started through sh.
F=/data/user/0/tech.nouverse.nouride.android/files
L=/data/app/~~HDEDNo1CRSKlzswtKChNSw==/tech.nouverse.nouride.android-UUevBkDF7RRFXQBntKTk6Q==/lib/arm64
S=$F/debian/sysdata
exec env -i PROOT_LOADER=$L/libproot-loader.so PROOT_TMP_DIR=$F/proot-tmp PROOT_L2S_DIR=$F/debian/rootfs/.l2s \
  HOME=/root PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8 TMPDIR=/tmp \
  /system/bin/sh -c 'exec "$@"' sh $L/libproot.so --kill-on-exit --link2symlink --sysvipc \
  '--kernel-release=\Linux\localhost\6.17.0-PRoot-Distro\#1 SMP PREEMPT_DYNAMIC Fri, 10 Oct 2025 00:00:00 +0000\aarch64\localdomain\-1\' \
  -L --change-id=0:0 --rootfs=$F/debian/rootfs --cwd=/root \
  --bind=/dev --bind=/proc --bind=/sys --bind=/dev/urandom:/dev/random \
  --bind=$S/sys_empty:/sys/fs/selinux \
  --bind=$S/loadavg:/proc/loadavg --bind=$S/stat:/proc/stat --bind=$S/uptime:/proc/uptime \
  --bind=$S/version:/proc/version --bind=$S/vmstat:/proc/vmstat \
  --bind=$S/sysctl_entry_cap_last_cap:/proc/sys/kernel/cap_last_cap \
  --bind=$S/sysctl_inotify_max_user_watches:/proc/sys/fs/inotify/max_user_watches \
  --bind=$S/sysctl_kernel_overflowuid:/proc/sys/kernel/overflowuid \
  --bind=$S/sysctl_kernel_overflowgid:/proc/sys/kernel/overflowgid \
  --bind=$F/debian/shm:/dev/shm \
  "$@"

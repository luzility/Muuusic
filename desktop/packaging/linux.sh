#!/usr/bin/env bash
set -euo pipefail

version="$1"
linuxdeploy="$2"
root="$(cd "$(dirname "$0")/../.." && pwd)"
binaries="$root/desktop/build/compose/binaries/main"
out="$root/desktop/build/release"
work="$(mktemp -d)"
mkdir -p "$out"

deb="$(ls "$binaries"/deb/*.deb)"
dpkg-deb -R "$deb" "$work/deb"
control="$work/deb/DEBIAN/control"
sed -i -e '/^\s*$/d' -e "s/^Version: .*/Version: ${version/-/\~}/" -e 's/^Depends: \(.*[^ ]\) *$/Depends: \1, libmpv2 | libmpv1/' "$control"
echo "Recommends: pulseaudio-utils | pipewire-bin" >> "$control"
fakeroot dpkg-deb --build "$work/deb" "$out/Muuusic-$version-amd64.deb"

appdir="$work/AppDir"
mkdir -p "$appdir/opt/muuusic" "$appdir/usr/bin"
cp -a "$binaries/app/Muuusic/." "$appdir/opt/muuusic/"
ln -s ../../opt/muuusic/bin/Muuusic "$appdir/usr/bin/Muuusic"
cat > "$work/AppRun" <<'EOF'
#!/bin/sh
here="$(dirname "$(readlink -f "$0")")"
export MUUUSIC_LIBMPV="$here/usr/lib/libmpv.so.2"
exec "$here/opt/muuusic/bin/Muuusic" "$@"
EOF
chmod +x "$work/AppRun"
cat > "$work/muuusic.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=Muuusic
Comment=Ad-free YouTube Music client
Exec=Muuusic
Icon=muuusic
Categories=AudioVideo;Audio;Player;
EOF
cp "$root/desktop/packaging/muuusic.png" "$work/muuusic.png"

cd "$work"
APPIMAGE_EXTRACT_AND_RUN=1 LINUXDEPLOY_OUTPUT_VERSION="$version" OUTPUT="$out/Muuusic-$version-x86_64.AppImage" \
    "$linuxdeploy" --appdir "$appdir" \
    --library "$(ldconfig -p | awk '/libmpv\.so\.2 / { print $NF; exit }')" \
    --desktop-file "$work/muuusic.desktop" \
    --icon-file "$work/muuusic.png" \
    --custom-apprun "$work/AppRun" \
    --output appimage

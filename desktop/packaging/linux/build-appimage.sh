#!/bin/sh
set -eu

if [ $# -ne 3 ]; then
    echo "usage: build-appimage.sh <app-image-dir> <icon.png> <output.AppImage>" >&2
    exit 2
fi

APP_IMAGE=$1
ICON=$2
OUTPUT=$3
HERE=$(dirname "$(readlink -f "$0")")
APPIMAGETOOL=${APPIMAGETOOL:-appimagetool}
LINUXDEPLOY=${LINUXDEPLOY:-linuxdeploy}
VLC_LIB_DIR=${VLC_LIB_DIR:-/usr/lib/x86_64-linux-gnu}
SSL_LIB_DIR=${SSL_LIB_DIR:-/usr/lib/x86_64-linux-gnu}

SKIPPED_PLUGINS="control gui lua services_discovery vaapi vdpau visualization"
KEPT_VIDEO_OUTPUTS="libvmem_plugin.so libvdummy_plugin.so"
HOST_LIBRARIES="
libglib-2.0.so* libgobject-2.0.so* libgio-2.0.so* libgmodule-2.0.so* libgthread-2.0.so*
libdbus-1.so* libsystemd.so* libudev.so* libapparmor.so* libselinux.so* libmount.so* libblkid.so*
libcrypto.so* libssl.so* libgcrypt.so* libgpg-error.so*
libkrb5.so* libkrb5support.so* libk5crypto.so* libgssapi_krb5.so* libkeyutils.so* libcom_err.so*
libz.so* libX11.so* libX11-xcb.so* libxcb*.so* libXext.so* libXrender.so* libXfixes.so* libXau.so*
libXdmcp.so* libXi.so* libXrandr.so* libXinerama.so* libXcursor.so* libXdamage.so* libXxf86vm.so*
libGL.so* libGLX.so* libGLdispatch.so* libEGL.so* libGLESv2.so* libOpenCL.so* libgbm.so* libdrm.so*
libva.so* libva-*.so* libvdpau.so* libwayland-*.so*
libcairo.so* libcairo-gobject.so* libpango-1.0.so* libpangocairo-1.0.so* libpangoft2-1.0.so*
libharfbuzz.so* libfreetype.so* libfontconfig.so* libgdk_pixbuf-2.0.so* libglycin-*.so*
libpulse.so* libpulse-simple.so* libasound.so* libjack.so*
"

APPDIR=$(dirname "$OUTPUT")/TeleDrive.AppDir
rm -rf "$APPDIR" "$OUTPUT"
mkdir -p "$(dirname "$APPDIR")"
cp -a "$APP_IMAGE" "$APPDIR"

LIBS=$APPDIR/usr/lib
PLUGINS=$LIBS/vlc/plugins
mkdir -p "$LIBS/vlc"
cp -a "$VLC_LIB_DIR"/libvlc.so.5* "$VLC_LIB_DIR"/libvlccore.so.9* "$LIBS/"
ln -sf libvlc.so.5 "$LIBS/libvlc.so"
ln -sf libvlccore.so.9 "$LIBS/libvlccore.so"
cp -a "$VLC_LIB_DIR/vlc/plugins" "$PLUGINS"
rm -f "$PLUGINS/plugins.dat"
for plugin in $SKIPPED_PLUGINS; do
    rm -rf "${PLUGINS:?}/$plugin"
done
for output in "$PLUGINS"/video_output/*.so; do
    case " $KEPT_VIDEO_OUTPUTS " in
        *" $(basename "$output") "*) ;;
        *) rm -f "$output" ;;
    esac
done

set -f --
for library in $HOST_LIBRARIES; do
    set -- "$@" --exclude-library "$library"
done
set +f
"$LINUXDEPLOY" --appdir "$APPDIR" "$@" \
    --deploy-deps-only "$LIBS/libvlc.so.5" \
    --deploy-deps-only "$LIBS/libvlccore.so.9" \
    --deploy-deps-only "$PLUGINS"

find "$PLUGINS" -name '*.so' -type f -exec patchelf --set-rpath '$ORIGIN/../../..' {} +

CACHE_GEN=$VLC_LIB_DIR/vlc/vlc-cache-gen
if [ -x "$CACHE_GEN" ]; then
    "$CACHE_GEN" "$PLUGINS"
fi

mkdir -p "$LIBS/openssl3"
cp -L "$SSL_LIB_DIR/libssl.so.3" "$SSL_LIB_DIR/libcrypto.so.3" "$LIBS/openssl3/"

install -m 755 "$HERE/AppRun" "$APPDIR/AppRun"
install -m 644 "$HERE/teledrive.desktop" "$APPDIR/teledrive.desktop"
install -m 644 "$ICON" "$APPDIR/teledrive.png"
install -m 644 "$ICON" "$APPDIR/.DirIcon"

ARCH=x86_64 "$APPIMAGETOOL" --no-appstream "$APPDIR" "$OUTPUT"

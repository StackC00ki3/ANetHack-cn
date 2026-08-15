#!/usr/bin/env sh
set -eu

nethack_dir=$1
lua_src=$2
cpp_dir=$3
assets_dir=$4
lua_compat_dir="lib/lua-5.4.8/src"
build_dir=$(mktemp -d)
trap 'rm -rf "$build_dir"' EXIT HUP INT TERM

git -C "$nethack_dir" archive --format=tar HEAD > "$build_dir/nethack.tar"
cd "$build_dir"
tar -xf nethack.tar
rm nethack.tar

if [ ! -f "$lua_src/lua.h" ]; then
    echo "Lua submodule is missing: $lua_src/lua.h" >&2
    exit 1
fi
if [ ! -f "$lua_compat_dir/lua.h" ]; then
    mkdir -p lib/lua-5.4.8
    rm -rf "$lua_compat_dir"
    if ! ln -s "$lua_src" "$lua_compat_dir" 2>/dev/null; then
        mkdir -p "$lua_compat_dir"
        cp -R "$lua_src"/. "$lua_compat_dir"/
    fi
fi

(cd sys/unix && ./setup.sh hints/linux.500)

rm -f util/makedefs util/makedefs.o util/dlb util/dlb_main.o src/dlb.o dat/options dat/nhdat

make -C dat all options
make dlb
if [ ! -s dat/nhdat ]; then
    echo "NetHack dat/nhdat was not generated." >&2
    find dat util -maxdepth 1 \( -name nhdat -o -name dlb \) -ls >&2
    exit 1
fi
make -C util ../src/tile.c
make -C dat nhtiles.bmp

mkdir -p "$assets_dir/nethackdir" "$assets_dir/tiles"

copy_if_changed() {
    src=$1
    dst=$2
    if [ ! -f "$dst" ] || ! cmp -s "$src" "$dst"; then
        cp "$src" "$dst"
    fi
}

copy_if_changed dat/nhdat "$assets_dir/nethackdir/nhdat"
copy_if_changed src/tile.c "$cpp_dir/tile.c"
copy_if_changed dat/nhtiles.bmp "$assets_dir/tiles/default_tiles_16.bmp"

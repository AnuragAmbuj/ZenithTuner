#!/bin/bash
set -e

# ZenithTuner Rust Core Build Script
echo "=== ZenithTuner: Compiling Rust DSP Core (rust-dsp) ==="

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
RUST_DIR="$PROJECT_ROOT/rust-dsp"
JNI_LIBS_DIR="$PROJECT_ROOT/app/src/main/jniLibs"

mkdir -p "$JNI_LIBS_DIR/arm64-v8a"
mkdir -p "$JNI_LIBS_DIR/x86_64"

cd "$RUST_DIR"

# Run tests first to ensure zero regressions
echo "[1/3] Running DSP test suite..."
cargo test --quiet

NDK_DEFAULT="/Users/anuragambuj/Library/Android/sdk/ndk/29.0.13113456"
if [ -z "$ANDROID_NDK_HOME" ] && [ -d "$NDK_DEFAULT" ]; then
    export ANDROID_NDK_HOME="$NDK_DEFAULT"
fi

echo "[2/3] Checking compilation targets..."
if command -v cargo-ndk >/dev/null 2>&1; then
    echo "Found cargo-ndk. Building arm64-v8a release binary..."
    cargo ndk -t arm64-v8a -o "$JNI_LIBS_DIR" build --release
elif [ -d "$ANDROID_NDK_HOME" ]; then
    CLANG_ARM64=$(ls "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android31-clang 2>/dev/null | head -n 1)
    if [ -x "$CLANG_ARM64" ]; then
        echo "Building arm64-v8a using NDK toolchain: $CLANG_ARM64"
        CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$CLANG_ARM64" cargo build --target aarch64-linux-android --release
        cp target/aarch64-linux-android/release/libzenith_dsp.so "$JNI_LIBS_DIR/arm64-v8a/"
    fi

    CLANG_X86_64=$(ls "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin/x86_64-linux-android31-clang 2>/dev/null | head -n 1)
    if [ -x "$CLANG_X86_64" ]; then
        echo "Building x86_64 using NDK toolchain: $CLANG_X86_64"
        CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$CLANG_X86_64" cargo build --target x86_64-linux-android --release
        cp target/x86_64-linux-android/release/libzenith_dsp.so "$JNI_LIBS_DIR/x86_64/"
    fi
else
    echo "Note: cargo-ndk not found or NDK path unset."
    echo "Building host release library..."
    cargo build --release
    
    # If aarch64 target is installed:
    if rustup target list | grep -q "aarch64-linux-android (installed)"; then
        echo "Building aarch64-linux-android..."
        cargo build --target aarch64-linux-android --release
        cp target/aarch64-linux-android/release/libzenith_dsp.so "$JNI_LIBS_DIR/arm64-v8a/" 2>/dev/null || true
    fi
fi

echo "[3/3] Build complete."
echo "Target JNI libraries directory: $JNI_LIBS_DIR"
ls -la "$JNI_LIBS_DIR"/* 2>/dev/null || true

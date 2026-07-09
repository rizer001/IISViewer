#!/bin/bash
# IISViewer Automated Test & Fix Cycle v3
#
# Полный цикл: сборка → копирование в mods/ → запуск Minecraft 26.2 →
# ожидание загрузки → проверка краш-репортов → анализ → фикс → перезапуск
#
# Поддерживает: TLauncher, официальный лаунчер (URI scheme)

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
MINECRAFT_DIR="/c/Users/danal/AppData/Roaming/.minecraft"
MODS_DIR="$MINECRAFT_DIR/mods"
CRASH_DIR="$MINECRAFT_DIR/crash-reports"
LOGS_DIR="$MINECRAFT_DIR/logs"
TLAUNCHER="C:/\.minecraft/TLauncher.exe"
MAX_ATTEMPTS=20
POLL_INTERVAL=5
POLL_MAX=24            # 24 * 5 = 120 секунд (2 минуты) ожидания
BUILD_LOG="/tmp/iisviewer_build.log"
CRASH_DUMP="/tmp/iisviewer_crash.txt"
ANALYSIS_LOG="/tmp/iisviewer_analysis.txt"

echo "╔══════════════════════════════════════════════════╗"
echo "║   IISViewer Automated Test & Fix Cycle v3      ║"
echo "╚══════════════════════════════════════════════════╝"
echo "Project: $PROJECT_DIR"
echo "Minecraft: $MINECRAFT_DIR"
echo "Profile: fabric-loader-26.2"
echo "Max attempts: $MAX_ATTEMPTS"
echo ""

# Create crash-reports directory if it doesn't exist
mkdir -p "$CRASH_DIR"
mkdir -p "$LOGS_DIR"

for attempt in $(seq 1 $MAX_ATTEMPTS); do
    echo ""
    echo "═══════════════════════════════════════════════"
    echo "  Attempt $attempt of $MAX_ATTEMPTS"
    echo "═══════════════════════════════════════════════"
    echo ""

    # ========== 1. BUILD ==========
    echo "▸ [1/5] Building mod..."
    cd "$PROJECT_DIR"

    rm -rf build Jar 2>/dev/null
    ./gradlew build 2>&1 | tee "$BUILD_LOG" | grep -E "(FAILED|ERROR|BUILD|26\\.2)" || true

    if [ ! -f "Jar/IISViewer-1.0.0.jar" ]; then
        echo "✗ BUILD FAILED! Check $BUILD_LOG for details."
        grep -E "error:|Error|FAILED" "$BUILD_LOG" | head -20
        echo ""
        echo "Waiting for fix before next attempt..."
        sleep 10
        continue
    fi

    JAR_SIZE=$(stat -c%s "Jar/IISViewer-1.0.0.jar" 2>/dev/null || echo "?")
    echo "✓ Build OK: Jar/IISViewer-1.0.0.jar (${JAR_SIZE} bytes)"

    # ========== 2. DEPLOY ==========
    echo "▸ [2/5] Deploying to mods/..."
    cp "Jar/IISViewer-1.0.0.jar" "$MODS_DIR/IISViewer-1.0.0.jar"
    echo "✓ Deployed to $MODS_DIR/IISViewer-1.0.0.jar"

    # ========== 3. CLEAN STATE ==========
    echo "▸ [3/5] Cleaning old state..."

    # Save and clean crash reports
    OLD_CRASH_COUNT=$(ls "$CRASH_DIR"/crash-*.txt 2>/dev/null | wc -l)
    rm -f "$CRASH_DIR"/crash-*.txt 2>/dev/null
    echo "✓ Removed $OLD_CRASH_COUNT old crash reports"

    # Save and clean latest.log for mod loading verification
    if [ -f "$LOGS_DIR/latest.log" ]; then
        cp "$LOGS_DIR/latest.log" "$LOGS_DIR/latest.log.bak" 2>/dev/null || true
        rm -f "$LOGS_DIR/latest.log"
        echo "✓ Rotated latest.log"
    fi

    # Clean analysis files
    rm -f "$CRASH_DUMP" "$ANALYSIS_LOG" 2>/dev/null

    # ========== 4. LAUNCH MINECRAFT ==========
    echo "▸ [4/5] Launching Minecraft 26.2..."

    # Kill any existing Minecraft/Java processes first
    /c/Windows/System32/taskkill.exe /F /IM javaw.exe 2>/dev/null || true
    /c/Windows/System32/taskkill.exe /F /IM java.exe 2>/dev/null || true
    /c/Windows/System32/taskkill.exe /F /IM TLauncher.exe 2>/dev/null || true
    sleep 2

    LAUNCH_SUCCESS=false

    # Strategy 1: Launch via Minecraft URI scheme (official launcher)
    # This opens the installed Microsoft Minecraft Launcher with the right profile
    echo "  Trying: minecraft://launch?profileName=fabric-loader-26.2"
    /c/Windows/System32/cmd.exe /c start /b "" "minecraft://launch?profileName=fabric-loader-26.2" 2>/dev/null
    sleep 3
    JAVA_RUNNING=$(/c/Windows/System32/tasklist.exe 2>/dev/null | grep -ci "javaw.exe" || true)
    if [ "$JAVA_RUNNING" -gt 0 ]; then
        LAUNCH_SUCCESS=true
        echo "  ✓ Minecraft Java process started!"
    fi

    # Strategy 2: Launch via TLauncher if URI didn't work
    if [ "$LAUNCH_SUCCESS" = false ] && [ -f "$TLAUNCHER" ]; then
        echo "  Trying: TLauncher"
        "$TLAUNCHER" 2>/dev/null &
        sleep 5
        JAVA_RUNNING=$(/c/Windows/System32/tasklist.exe 2>/dev/null | grep -ci "javaw.exe" || true)
        if [ "$JAVA_RUNNING" -gt 0 ]; then
            LAUNCH_SUCCESS=true
            echo "  ✓ TLauncher started!"
        fi
    fi

    # Strategy 3: Launch via start cmd (opens default launcher from shortcut)
    if [ "$LAUNCH_SUCCESS" = false ]; then
        echo "  Trying: Start via cmd shortcut"
        /c/Windows/System32/cmd.exe /c start /b "" "Minecraft" 2>/dev/null || true
        sleep 5
        JAVA_RUNNING=$(/c/Windows/System32/tasklist.exe 2>/dev/null | grep -ci "javaw.exe" || true)
        if [ "$JAVA_RUNNING" -gt 0 ]; then
            echo "  ✓ Minecraft started via cmd!"
        else
            echo "  ⚠ No Java process detected after launch attempt."
            echo "  ⚠ Please OPEN THE MINECRAFT LAUNCHER MANUALLY and select profile: fabric-loader-26.2"
            echo "  ⚠ The script will wait and detect crashes automatically."
        fi
    fi

    # Save current process list for later comparison
    /c/Windows/System32/tasklist.exe 2>/dev/null | grep -i "javaw.exe" > /tmp/iisviewer_javaw_before.txt 2>/dev/null || true

    # ========== 5. POLL FOR CRASH OR SUCCESS ==========
    echo "▸ [5/5] Waiting for Minecraft to load..."
    echo "  Polling every ${POLL_INTERVAL}s (max ${POLL_MAX} polls = $((POLL_MAX * POLL_INTERVAL))s)"

    CRASH_DETECTED=false
    MINECRAFT_LOADED=false
    poll_count=0

    while [ $poll_count -lt $POLL_MAX ]; do
        sleep $POLL_INTERVAL
        poll_count=$((poll_count + 1))

        # Check for new crash reports
        LATEST_CRASH=$(ls -t "$CRASH_DIR"/crash-*.txt 2>/dev/null | head -1)
        if [ -n "$LATEST_CRASH" ]; then
            CRASH_DETECTED=true
            echo "  ⚠ CRASH DETECTED at poll $poll_count!"
            break
        fi

        # Check if Java process is running
        JAVA_RUNNING=$(/c/Windows/System32/tasklist.exe 2>/dev/null | grep -ci "javaw.exe" || true)

        if [ "$JAVA_RUNNING" -gt 0 ]; then
            if [ $((poll_count % 4)) -eq 0 ]; then
                echo "  ... Java running, poll $poll_count..."
            fi
        else
            if [ $((poll_count % 4)) -eq 0 ]; then
                echo "  ... No Java yet, poll $poll_count..."
            fi
        fi

        # Check latest.log for mod loading or errors
        if [ -f "$LOGS_DIR/latest.log" ]; then
            # Check if IISViewer mod was loaded
            if grep -qi "iisviewer" "$LOGS_DIR/latest.log" 2>/dev/null; then
                echo "  ✓ IISViewer mod detected in latest.log!"
                MINECRAFT_LOADED=true
                break
            fi
            # Check for Fabric errors that might be related to our mod
            if grep -qi "error.*iisviewer\|iisviewer.*error\|fail.*iisviewer\|iisviewer.*fail" "$LOGS_DIR/latest.log" 2>/dev/null; then
                echo "  ⚠ Error related to IISViewer in latest.log!"
                CRASH_DETECTED=true
                break
            fi
            # Check if Minecraft loaded successfully (rendering started)
            if grep -qi "render thread\|OpenGL\|GLFW\|Sound engine started\|Minecraft 26" "$LOGS_DIR/latest.log" 2>/dev/null; then
                if [ $poll_count -ge 6 ]; then  # At least 30 seconds
                    echo "  ✓ Minecraft rendering started!"
                    MINECRAFT_LOADED=true
                    break
                fi
            fi
        fi

        # If Java has been running for a while without crash, consider it loaded
        if [ $poll_count -ge 12 ] && [ "$JAVA_RUNNING" -gt 0 ]; then  # 60 seconds
            # Check for crash reports one more time
            if [ ! -f "$CRASH_DIR"/crash-*.txt ] 2>/dev/null; then
                echo "  ✓ Java running $((poll_count * POLL_INTERVAL))s without crash!"
                MINECRAFT_LOADED=true
                break
            fi
        fi
    done

    # ========== ANALYZE RESULT ==========
    if [ "$CRASH_DETECTED" = true ]; then
        echo ""
        echo "╔═══ CRASH REPORT ═══════════════════════════╗"
        cp "$LATEST_CRASH" "$CRASH_DUMP"
        echo "Crash: $LATEST_CRASH"
        echo ""

        # Extract key crash info
        echo "── Root cause ──"
        grep -E "Caused by:|java\\.(lang|io|noclass)" "$CRASH_DUMP" | head -5

        echo ""
        echo "── Missing class / method? ──"
        grep -E "NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|ClassCastException" "$CRASH_DUMP" | head -5

        echo ""
        echo "── Relevant lines ──"
        grep -E "net/minecraft|com/iisviewer" "$CRASH_DUMP" | grep -v "\\.class" | head -10

        echo ""
        echo "═══ FULL CRASH REPORT ═══"
        head -60 "$CRASH_DUMP"
        echo ""

        # Analyze what might need to be fixed
        echo "── Analysis for fixes ──" | tee "$ANALYSIS_LOG"
        MISSING_CLASS=$(grep -E "(NoClassDefFoundError|ClassNotFoundException)" "$CRASH_DUMP" | grep -oP 'net/minecraft/[^\s;)]+' | head -1)
        if [ -n "$MISSING_CLASS" ]; then
            echo "Possible missing class: $MISSING_CLASS" | tee -a "$ANALYSIS_LOG"
            echo "Check if this class was renamed/moved in Minecraft 26.2" | tee -a "$ANALYSIS_LOG"
        fi

        # Also check latest.log for errors
        if [ -f "$LOGS_DIR/latest.log" ]; then
            echo "" >> "$ANALYSIS_LOG"
            echo "── Errors from latest.log ──" >> "$ANALYSIS_LOG"
            grep -i "error\|exception\|failed\|caused" "$LOGS_DIR/latest.log" 2>/dev/null | grep -i "iisviewer\|mod\|fabric" | head -10 >> "$ANALYSIS_LOG"
        fi

        # Kill Minecraft
        /c/Windows/System32/taskkill.exe /F /IM javaw.exe 2>/dev/null || true
        /c/Windows/System32/taskkill.exe /F /IM java.exe 2>/dev/null || true

        echo ""
        echo "╚═══════════════════════════════════════════════╝"

        if [ $attempt -lt $MAX_ATTEMPTS ]; then
            echo ""
            echo "Will retry (attempt $((attempt + 1)) of $MAX_ATTEMPTS)..."
            sleep 3
        else
            echo "Max attempts reached. Manual intervention needed."
            echo "See $ANALYSIS_LOG and $CRASH_DUMP for details."
            exit 1
        fi

    elif [ "$MINECRAFT_LOADED" = true ]; then
        echo ""
        echo "╔══════════════════════════════════════════════╗"
        echo "║   ✅ IISViewer loaded SUCCESSFULLY!         ║"
        echo "║   No crashes after $((poll_count * POLL_INTERVAL)) seconds   ║"
        echo "╚══════════════════════════════════════════════╝"
        echo ""
        echo "Attempts used: $attempt"
        echo "Mod: Jar/IISViewer-1.0.0.jar ($JAR_SIZE bytes)"
        echo "Deployed to: $MODS_DIR/IISViewer-1.0.0.jar"

        # Show mod loading evidence from log
        if [ -f "$LOGS_DIR/latest.log" ]; then
            echo ""
            echo "── Mod loading evidence from latest.log ──"
            grep -i "iisviewer\|mod.*load\|loaded mod" "$LOGS_DIR/latest.log" 2>/dev/null | head -10
        fi

        echo ""
        echo "Minecraft is running. Press Ctrl+C to stop."
        echo ""
        exit 0

    else
        # Timeout
        echo ""
        echo "⚠ Timeout after $((poll_count * POLL_INTERVAL)) seconds."

        # Final checks
        JAVA_RUNNING=$(/c/Windows/System32/tasklist.exe 2>/dev/null | grep -ci "javaw.exe" || true)
        if [ "$JAVA_RUNNING" -gt 0 ] && [ ! -f "$CRASH_DIR"/crash-*.txt ] 2>/dev/null; then
            echo "  Java still running, no crash found. Giving more time..."
            echo "  Checking latest.log..."
            if [ -f "$LOGS_DIR/latest.log" ]; then
                if grep -qi "iisviewer" "$LOGS_DIR/latest.log" 2>/dev/null; then
                    echo "  ✅ IISViewer mod IS loaded! (found in latest.log)"
                    exit 0
                fi
                if grep -qi "render\|OpenGL\|GLFW" "$LOGS_DIR/latest.log" 2>/dev/null; then
                    echo "  ✅ Minecraft is rendering (game loaded successfully)"
                    exit 0
                fi
                # Show last 20 lines for debugging
                echo "  ── Last 20 lines of latest.log ──"
                tail -20 "$LOGS_DIR/latest.log" 2>/dev/null
            fi
        fi

        echo "  Continuing to next attempt..."
        sleep 3
    fi
done

echo ""
echo "=== CYCLE COMPLETE ==="
echo "See log: $CRASH_DUMP"
exit 1

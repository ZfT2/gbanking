package de.zft2.gbanking.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

public class UpdateInstallerLauncher {

	private static final Set<PosixFilePermission> EXECUTABLE_PERMISSIONS = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
			PosixFilePermission.OWNER_EXECUTE);
	private static final String[] ROOT_FILES = { "README.md", "CHANGELOG.md", "CONTRIBUTING.md", "LICENSE", "NOTICE.txt" };
	private static final String INSTITUTE_DATABASE_PATH = "data/institute.db";
	private static final String POWERSHELL_EXECUTABLE = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
	private static final String SHELL_EXECUTABLE = "/bin/sh";

	private final OperatingSystem operatingSystem;

	public UpdateInstallerLauncher() {
		this(OperatingSystem.current());
	}

	UpdateInstallerLauncher(OperatingSystem operatingSystem) {
		this.operatingSystem = operatingSystem;
	}

	public void launch(PreparedUpdate preparedUpdate) throws IOException, UpdateException {
		Path script = createInstallerScript(preparedUpdate, ProcessHandle.current().pid(), true);
		startInstaller(script);
	}

	Path createInstallerScript(PreparedUpdate preparedUpdate, long processId, boolean restartApplication) throws IOException, UpdateException {
		validate(preparedUpdate);
		return operatingSystem.isWindows() ? writeWindowsInstaller(preparedUpdate, processId, restartApplication)
				: writeShellInstaller(preparedUpdate, processId, restartApplication);
	}

	private void validate(PreparedUpdate preparedUpdate) throws UpdateException {
		if (!Files.isDirectory(preparedUpdate.installDirectory().resolve("bin")) || !Files.isDirectory(preparedUpdate.installDirectory().resolve("lib"))) {
			throw new UpdateException("Current installation is not a release distribution");
		}
		if (!Files.isDirectory(preparedUpdate.sourceDirectory().resolve("bin")) || !Files.isDirectory(preparedUpdate.sourceDirectory().resolve("lib"))) {
			throw new UpdateException("Prepared update is not a release distribution");
		}
		if (!Files.isRegularFile(preparedUpdate.sourceDirectory().resolve(INSTITUTE_DATABASE_PATH))) {
			throw new UpdateException("Prepared update does not contain data/institute.db");
		}
	}

	private Path writeWindowsInstaller(PreparedUpdate preparedUpdate, long processId, boolean restartApplication) throws IOException {
		Path script = preparedUpdate.workDirectory().resolve("install-update.ps1");
		Path backupDirectory = preparedUpdate.workDirectory().resolve("backup");
		String scriptContent = """
				$ErrorActionPreference = 'Stop'
				$install = %s
				$source = %s
				$backup = %s
				$log = %s
				$rootFiles = @(%s)
				$instituteSource = Join-Path $source 'data/institute.db'
				$instituteTarget = Join-Path $install 'data/institute.db'
				$instituteBackup = Join-Path $backup 'data/institute.db'
				$script:instituteUpdateStarted = $false
				$script:instituteExisted = $false
				$restartApplication = %s

				function Write-UpdateLog {
				    param([string]$Message)
				    $line = ('{0} {1}' -f (Get-Date -Format o), $Message)
				    Add-Content -LiteralPath $log -Value $line
				}

				function Restore-Backup {
				    foreach ($name in @('bin', 'lib')) {
				        $backupPath = Join-Path $backup $name
				        $targetPath = Join-Path $install $name
				        if (Test-Path -LiteralPath $backupPath) {
				            if (Test-Path -LiteralPath $targetPath) {
				                Remove-Item -LiteralPath $targetPath -Recurse -Force
				            }
				            Move-Item -LiteralPath $backupPath -Destination $targetPath -Force
				        }
				    }
				    if ($script:instituteUpdateStarted) {
				        if (Test-Path -LiteralPath $instituteTarget) {
				            Remove-Item -LiteralPath $instituteTarget -Force
				        }
				        if ($script:instituteExisted -and (Test-Path -LiteralPath $instituteBackup)) {
				            New-Item -ItemType Directory -Path (Split-Path -Parent $instituteTarget) -Force | Out-Null
				            Move-Item -LiteralPath $instituteBackup -Destination $instituteTarget -Force
				        }
				    }
				}

				try {
				    Write-UpdateLog 'Waiting for GBanking to exit.'
				    Wait-Process -Id %d -ErrorAction SilentlyContinue
				    New-Item -ItemType Directory -Path $backup -Force | Out-Null

				    foreach ($name in @('bin', 'lib')) {
				        $currentPath = Join-Path $install $name
				        $backupPath = Join-Path $backup $name
				        if (Test-Path -LiteralPath $currentPath) {
				            Move-Item -LiteralPath $currentPath -Destination $backupPath -Force
				        }
				    }

				    foreach ($name in @('bin', 'lib')) {
				        Copy-Item -LiteralPath (Join-Path $source $name) -Destination (Join-Path $install $name) -Recurse -Force
				    }

				    if (Test-Path -LiteralPath $instituteTarget) {
				        $script:instituteExisted = $true
				        New-Item -ItemType Directory -Path (Split-Path -Parent $instituteBackup) -Force | Out-Null
				        Copy-Item -LiteralPath $instituteTarget -Destination $instituteBackup -Force
				    }
				    New-Item -ItemType Directory -Path (Split-Path -Parent $instituteTarget) -Force | Out-Null
				    $script:instituteUpdateStarted = $true
				    Copy-Item -LiteralPath $instituteSource -Destination $instituteTarget -Force

				    foreach ($name in $rootFiles) {
				        $sourceFile = Join-Path $source $name
				        if (Test-Path -LiteralPath $sourceFile) {
				            Copy-Item -LiteralPath $sourceFile -Destination (Join-Path $install $name) -Force
				        }
				    }

				    if ($restartApplication) {
				        Write-UpdateLog 'Starting updated GBanking.'
				        Start-Process -FilePath (Join-Path (Join-Path $install 'bin') 'gbanking.bat') -WorkingDirectory $install
				    }
				} catch {
				    Write-UpdateLog ('Update failed: ' + $_.Exception.Message)
				    Restore-Backup
				    exit 1
				}
				""".formatted(psQuote(preparedUpdate.installDirectory()), psQuote(preparedUpdate.sourceDirectory()), psQuote(backupDirectory),
				psQuote(preparedUpdate.workDirectory().resolve("install-update.log")), psArray(ROOT_FILES), restartApplication ? "$true" : "$false",
				processId);
		Files.writeString(script, scriptContent, StandardCharsets.UTF_8);
		return script;
	}

	private Path writeShellInstaller(PreparedUpdate preparedUpdate, long processId, boolean restartApplication) throws IOException {
		Path script = preparedUpdate.workDirectory().resolve("install-update.sh");
		Path backupDirectory = preparedUpdate.workDirectory().resolve("backup");
		String scriptContent = """
				#!/usr/bin/env sh
				set -u

				PID=%d
				INSTALL=%s
				SOURCE=%s
				BACKUP=%s
				LOG=%s
				ROOT_FILES="%s"
				LAUNCHER=%s
				INSTITUTE_SOURCE="$SOURCE/data/institute.db"
				INSTITUTE_TARGET="$INSTALL/data/institute.db"
				INSTITUTE_BACKUP="$BACKUP/data/institute.db"
				INSTITUTE_UPDATE_STARTED=0
				INSTITUTE_EXISTED=0
				RESTART_APPLICATION=%d

				log() {
				    printf '%%s %%s\\n' "$(date -u +%%Y-%%m-%%dT%%H:%%M:%%SZ)" "$1" >> "$LOG"
				}

				restore_backup() {
				    for name in bin lib; do
				        if [ -d "$BACKUP/$name" ]; then
				            rm -rf "$INSTALL/$name"
				            mv "$BACKUP/$name" "$INSTALL/$name"
				        fi
				    done
				    if [ "$INSTITUTE_UPDATE_STARTED" -eq 1 ]; then
				        rm -f "$INSTITUTE_TARGET"
				        if [ "$INSTITUTE_EXISTED" -eq 1 ] && [ -f "$INSTITUTE_BACKUP" ]; then
				            mkdir -p "$(dirname "$INSTITUTE_TARGET")"
				            mv "$INSTITUTE_BACKUP" "$INSTITUTE_TARGET"
				        fi
				    fi
				}

				fail() {
				    log "$1"
				    restore_backup
				    exit 1
				}

				log "Waiting for GBanking to exit."
				while kill -0 "$PID" 2>/dev/null; do
				    sleep 1
				done

				mkdir -p "$BACKUP" || fail "Could not create backup directory."

				for name in bin lib; do
				    if [ -d "$INSTALL/$name" ]; then
				        mv "$INSTALL/$name" "$BACKUP/$name" || fail "Could not move $name to backup."
				    fi
				done

				for name in bin lib; do
				    cp -R "$SOURCE/$name" "$INSTALL/$name" || fail "Could not copy $name."
				done

				if [ -f "$INSTITUTE_TARGET" ]; then
				    mkdir -p "$(dirname "$INSTITUTE_BACKUP")" || fail "Could not create institute database backup directory."
				    cp "$INSTITUTE_TARGET" "$INSTITUTE_BACKUP" || fail "Could not back up institute database."
				    INSTITUTE_EXISTED=1
				fi
				mkdir -p "$(dirname "$INSTITUTE_TARGET")" || fail "Could not create institute database directory."
				INSTITUTE_UPDATE_STARTED=1
				cp "$INSTITUTE_SOURCE" "$INSTITUTE_TARGET" || fail "Could not copy institute database."

				for name in $ROOT_FILES; do
				    if [ -f "$SOURCE/$name" ]; then
				        cp "$SOURCE/$name" "$INSTALL/$name" || fail "Could not copy $name."
				    fi
				done

				chmod +x "$INSTALL/bin/gbanking.sh" "$INSTALL/bin/gbanking.command" 2>/dev/null || true

				if [ "$RESTART_APPLICATION" -eq 1 ]; then
				    log "Starting updated GBanking."
				    cd "$INSTALL" || fail "Could not switch to installation directory."
				    nohup "$INSTALL/bin/$LAUNCHER" >/dev/null 2>&1 &
				fi
				exit 0
				""".formatted(processId, shQuote(preparedUpdate.installDirectory().toString()),
				shQuote(preparedUpdate.sourceDirectory().toString()), shQuote(backupDirectory.toString()),
				shQuote(preparedUpdate.workDirectory().resolve("install-update.log").toString()), String.join(" ", ROOT_FILES),
				shQuote(operatingSystem.launcherName()), restartApplication ? 1 : 0);
		Files.writeString(script, scriptContent, StandardCharsets.UTF_8);
		makeExecutable(script);
		return script;
	}

	private void startInstaller(Path script) throws IOException {
		if (operatingSystem.isWindows()) {
			new ProcessBuilder(POWERSHELL_EXECUTABLE, "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script.toString())
					.start();
			return;
		}
		new ProcessBuilder(SHELL_EXECUTABLE, script.toString()).start();
	}

	private void makeExecutable(Path script) throws IOException {
		try {
			Files.setPosixFilePermissions(script, EXECUTABLE_PERMISSIONS);
		} catch (UnsupportedOperationException ignored) {
			// The script is still executable through "sh script" on non-POSIX file systems.
		}
	}

	private String psArray(String[] values) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				result.append(", ");
			}
			result.append(psQuote(values[i]));
		}
		return result.toString();
	}

	private String psQuote(Path path) {
		return psQuote(path.toAbsolutePath().normalize().toString());
	}

	private String psQuote(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private String shQuote(String value) {
		return "'" + value.replace("'", "'\"'\"'") + "'";
	}
}

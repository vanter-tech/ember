#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use rand::RngCore;
use std::collections::HashMap;
use std::io::{BufRead, BufReader, Write};
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager, WindowEvent};
use tauri_plugin_autostart::{MacosLauncher, ManagerExt};

struct PortState(Arc<Mutex<u16>>);
struct AgentProcessState(Arc<Mutex<Option<Child>>>);

#[tauri::command]
fn get_port(state: tauri::State<PortState>) -> u16 {
    *state.0.lock().unwrap()
}

/// `%ProgramData%\EmberHub\hub.env` — same file `Iniciar Ember Hub.cmd` used to read before
/// launching the app; this shell now owns both writing it (first run) and reading it (every run),
/// replacing that batch shim and the Inno Setup `[Code]` section that used to generate it.
fn hub_env_path() -> PathBuf {
    let program_data = std::env::var("ProgramData").unwrap_or_else(|_| "C:\\ProgramData".into());
    PathBuf::from(program_data).join("EmberHub").join("hub.env")
}

fn random_hex(bytes: usize) -> String {
    let mut buf = vec![0u8; bytes];
    rand::thread_rng().fill_bytes(&mut buf);
    buf.iter().map(|b| format!("{:02x}", b)).collect()
}

/// Creates `hub.env` with fresh secrets if it does not exist yet (first run after install).
/// Never overwrites an existing file — this is the one-time bootstrap the old Inno Setup
/// installer used to do in `ssPostInstall`; doing it here instead of in NSIS lets it use a real
/// CSPRNG (`rand`) instead of hand-rolled Pascal LCG, and keeps the logic testable/versioned in
/// the same codebase as the rest of the shell.
fn ensure_hub_env(app_dir: &std::path::Path) -> std::io::Result<()> {
    let path = hub_env_path();
    if path.exists() {
        return Ok(());
    }
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
        std::fs::create_dir_all(parent.join("data").join("postgres"))?;
        std::fs::create_dir_all(parent.join("data").join("minio"))?;
        std::fs::create_dir_all(parent.join("logs"))?;
        std::fs::create_dir_all(parent.join("backups"))?;
    }
    let program_data = path.parent().unwrap().to_string_lossy().to_string();
    let contents = format!(
        "# Generado por Ember Hub al primer arranque. No editar salvo el puerto.\n\
         EMBER_HUB_DATA_DIR={pd}\\data\\postgres\n\
         EMBER_HUB_MINIO_DATA_DIR={pd}\\data\\minio\n\
         EMBER_HUB_POSTGRES_BIN_DIR={app}\\pgsql\\bin\n\
         EMBER_HUB_MINIO_BIN_DIR={app}\\minio\n\
         EMBER_HUB_LICENSE_FILE={pd}\\license.key\n\
         EMBER_HUB_PUBLIC_KEY_FILE={app}\\hub-public-key.der\n\
         EMBER_HUB_STATE_FILE={pd}\\hub-state.json\n\
         EMBER_HUB_POSTGRES_PORT=5432\n\
         EMBER_HUB_MINIO_PORT=9000\n\
         EMBER_HUB_SERVER_PORT=8080\n\
         EMBER_HUB_ACTIVATION_URL=https://api.ember.vanter.net/v1/hub-activations\n\
         EMBER_HUB_HEARTBEAT_URL=https://api.ember.vanter.net/v1/hub-heartbeat\n\
         JWT_SECRET={jwt}\n\
         PLATFORM_JWT_SECRET={pjwt}\n",
        pd = program_data,
        app = app_dir.display(),
        jwt = random_hex(32),
        pjwt = random_hex(32)
    );
    let mut file = std::fs::File::create(&path)?;
    file.write_all(contents.as_bytes())
}

fn read_hub_env() -> HashMap<String, String> {
    let mut map = HashMap::new();
    if let Ok(contents) = std::fs::read_to_string(hub_env_path()) {
        for line in contents.lines() {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            if let Some((key, value)) = line.split_once('=') {
                map.insert(key.trim().to_string(), value.trim().to_string());
            }
        }
    }
    map
}

/// Tauri's `resource_dir()` returns a canonicalized path, which on Windows carries a `\\?\`
/// verbatim-path prefix. Java's `java.nio.file.Path.of(...)` (used by `HubProperties.fromEnvironment`
/// for every `EMBER_HUB_*_DIR`/`_FILE` env var) cannot parse that prefix at all and throws
/// `InvalidPathException` — so any path baked into `hub.env` must have it stripped first, or the
/// sidecar crashes on every single first launch, not just on this machine.
fn strip_verbatim_prefix(path: &std::path::Path) -> PathBuf {
    let s = path.to_string_lossy();
    PathBuf::from(s.strip_prefix(r"\\?\").unwrap_or(&s))
}

/// `Child::kill()` only terminates the sidecar process itself via `TerminateProcess` — it does not
/// cascade to grandchildren. `Ember Hub.exe` (the JVM) spawns `postgres.exe`/`minio.exe` as its own
/// children, so killing just the JVM orphans them: they keep holding ports 5432/9000 forever, and
/// the *next* launch's fresh Postgres/MinIO then fails with "port already in use" against its own
/// previous instance. `taskkill /T` kills the whole process tree, not just the one PID.
fn kill_process_tree(child: &Child) {
    let _ = Command::new("taskkill")
        .args(["/F", "/T", "/PID", &child.id().to_string()])
        .output();
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn strips_verbatim_prefix_when_present() {
        let input = PathBuf::from(r"\\?\C:\Users\ferob\app-image\Ember Hub");
        assert_eq!(strip_verbatim_prefix(&input), PathBuf::from(r"C:\Users\ferob\app-image\Ember Hub"));
    }

    #[test]
    fn leaves_normal_path_unchanged() {
        let input = PathBuf::from(r"C:\Users\ferob\app-image\Ember Hub");
        assert_eq!(strip_verbatim_prefix(&input), input);
    }
}

fn spawn_agent(app: &AppHandle, port_state: Arc<Mutex<u16>>, agent_process: Arc<Mutex<Option<Child>>>) {
    let resource_dir = strip_verbatim_prefix(&app.path().resource_dir().expect("no resource dir"));
    let app_dir = resource_dir.join("app-image").join("Ember Hub");
    let exe = app_dir.join("Ember Hub.exe");

    ensure_hub_env(&app_dir).expect("failed to write hub.env");
    let env = read_hub_env();

    let mut command = Command::new(exe);
    command.env("SPRING_PROFILES_ACTIVE", "hub").envs(&env).stdout(Stdio::piped());
    let mut child = command.spawn().expect("failed to spawn Ember Hub sidecar");

    let stdout = child.stdout.take().expect("no stdout from sidecar");
    *agent_process.lock().unwrap() = Some(child);

    // Reads the "PORT=<n>" line EmberApplication's hub sidecar prints once HubControlServer is
    // listening.
    let app_handle = app.clone();
    std::thread::spawn(move || {
        let reader = BufReader::new(stdout);
        for line in reader.lines().flatten() {
            if let Some(rest) = line.strip_prefix("PORT=") {
                if let Ok(port) = rest.trim().parse::<u16>() {
                    *port_state.lock().unwrap() = port;
                    let _ = app_handle.emit("agent-ready", port);
                }
            }
        }
    });

    // Polls the same Child for an unexpected exit (crash) so the UI can show an error + retry.
    let app_handle_watch = app.clone();
    let watch_process = agent_process.clone();
    std::thread::spawn(move || loop {
        std::thread::sleep(std::time::Duration::from_secs(2));
        let mut guard = watch_process.lock().unwrap();
        match guard.as_mut() {
            Some(child) => match child.try_wait() {
                Ok(Some(_status)) => {
                    let _ = app_handle_watch.emit("agent-crashed", ());
                    *guard = None;
                    break;
                }
                Ok(None) => {}
                Err(_) => break,
            },
            None => break,
        }
    });
}

#[tauri::command]
fn restart_agent(
    app: AppHandle,
    port_state: tauri::State<PortState>,
    agent_process: tauri::State<AgentProcessState>,
) {
    if let Some(child) = agent_process.0.lock().unwrap().take() {
        kill_process_tree(&child);
    }
    *port_state.0.lock().unwrap() = 0;
    spawn_agent(&app, port_state.0.clone(), agent_process.0.clone());
}

fn main() {
    let port_state = Arc::new(Mutex::new(0u16));
    let agent_process: Arc<Mutex<Option<Child>>> = Arc::new(Mutex::new(None));
    let port_state_setup = port_state.clone();
    let agent_process_setup = agent_process.clone();
    let agent_process_exit = agent_process.clone();

    tauri::Builder::default()
        .plugin(tauri_plugin_autostart::init(MacosLauncher::LaunchAgent, None))
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_process::init())
        .manage(PortState(port_state.clone()))
        .manage(AgentProcessState(agent_process.clone()))
        .invoke_handler(tauri::generate_handler![get_port, restart_agent])
        .setup(move |app| {
            spawn_agent(app.handle(), port_state_setup.clone(), agent_process_setup.clone());

            let autostart = app.autolaunch();
            let _ = autostart.enable();

            let show = MenuItem::with_id(app, "show", "Mostrar Ember Hub", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "Salir", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show, &quit])?;

            let _tray = TrayIconBuilder::new()
                .menu(&menu)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "show" => {
                        if let Some(w) = app.get_webview_window("main") {
                            let _ = w.show();
                            let _ = w.set_focus();
                        }
                    }
                    "quit" => app.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click { .. } = event {
                        if let Some(w) = tray.app_handle().get_webview_window("main") {
                            let _ = w.show();
                            let _ = w.set_focus();
                        }
                    }
                })
                .build(app)?;

            Ok(())
        })
        .on_window_event(|_window, event| {
            // Mirrors HubDashboard's old windowClosing → onExit: closing the window stops the
            // sidecar. (Printer-agent hides to tray instead — Hub keeps its original "closing
            // means shutting down the services" behavior since a headless Hub with the window
            // hidden would otherwise silently keep Postgres/MinIO/the server running with no
            // visible way to tell; the tray's "Mostrar" still exists to bring the window back
            // while services are up.)
            if let WindowEvent::CloseRequested { .. } = event {
                // no-op: default behavior (window closes, app.run's Exit handler below fires)
            }
        })
        .build(tauri::generate_context!())
        .expect("error while building the Ember Hub shell")
        .run(move |_app_handle, event| {
            if let tauri::RunEvent::Exit = event {
                if let Some(child) = agent_process_exit.lock().unwrap().take() {
                    kill_process_tree(&child);
                }
            }
        });
}

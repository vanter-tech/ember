#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::io::{BufRead, BufReader};
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

#[tauri::command]
fn open_folder(path: String) -> Result<(), String> {
    Command::new("explorer").arg(path).spawn().map(|_| ()).map_err(|e| e.to_string())
}

fn spawn_agent(app: &AppHandle, port_state: Arc<Mutex<u16>>, agent_process: Arc<Mutex<Option<Child>>>) {
    let resource_dir = app.path().resource_dir().expect("no resource dir");
    let exe = resource_dir.join("app-image").join("Ember Agent").join("Ember Agent.exe");

    let mut child = Command::new(exe)
        .stdout(Stdio::piped())
        .spawn()
        .expect("failed to spawn Ember Agent sidecar");

    let stdout = child.stdout.take().expect("no stdout from sidecar");
    *agent_process.lock().unwrap() = Some(child);

    // Reads the "PORT=<n>" line Main.java prints once LocalControlServer is listening.
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

    // Polls the same Child for an unexpected exit (crash) so the UI can show an error + retry
    // instead of silently going stale, per spec §4's "sidecar muere en caliente".
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
    if let Some(mut child) = agent_process.0.lock().unwrap().take() {
        let _ = child.kill();
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
        .manage(PortState(port_state.clone()))
        .manage(AgentProcessState(agent_process.clone()))
        .invoke_handler(tauri::generate_handler![get_port, open_folder, restart_agent])
        .setup(move |app| {
            spawn_agent(app.handle(), port_state_setup.clone(), agent_process_setup.clone());

            let autostart = app.autolaunch();
            let _ = autostart.enable();

            let show = MenuItem::with_id(app, "show", "Mostrar Ember Agent", true, None::<&str>)?;
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
        .on_window_event(|window, event| {
            // Mirrors AgentDashboard's old windowClosing handler: minimize to tray, never exit.
            if let WindowEvent::CloseRequested { api, .. } = event {
                let _ = window.hide();
                api.prevent_close();
            }
        })
        .build(tauri::generate_context!())
        .expect("error while building the Ember Agent shell")
        .run(move |_app_handle, event| {
            // Kill the sidecar on real app exit so a force-killed/updated shell never leaves an
            // orphaned Java process behind (spec §7 "cierre de proceso huérfano").
            if let tauri::RunEvent::Exit = event {
                if let Some(mut child) = agent_process_exit.lock().unwrap().take() {
                    let _ = child.kill();
                }
            }
        });
}

use chrono::{Datelike, Local, NaiveDate, Utc};
use eframe::egui::{self, Align2, Color32, RichText};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::sync::OnceLock;

static DATA_DIR: OnceLock<PathBuf> = OnceLock::new();

#[derive(Clone, Serialize, Deserialize)]
struct Entry {
    id: String,
    name: String,
    #[serde(rename = "type", default = "default_kind")]
    kind: String,
    date: String,
    #[serde(default = "default_remind")]
    remind: i64,
    #[serde(default)]
    rel: String,
}

fn default_kind() -> String {
    "birthday".to_string()
}

fn default_remind() -> i64 {
    3
}

fn occurrence_in(base: NaiveDate, year: i32) -> Option<NaiveDate> {
    NaiveDate::from_ymd_opt(year, base.month(), base.day())
        .or_else(|| NaiveDate::from_ymd_opt(year, base.month(), 28))
}

impl Entry {
    fn base(&self) -> Option<NaiveDate> {
        NaiveDate::parse_from_str(&self.date, "%Y-%m-%d").ok()
    }

    fn next(&self, today: NaiveDate) -> Option<NaiveDate> {
        let base = self.base()?;
        let this_year = occurrence_in(base, today.year())?;
        if this_year < today {
            occurrence_in(base, today.year() + 1)
        } else {
            Some(this_year)
        }
    }

    fn days(&self, today: NaiveDate) -> Option<i64> {
        Some((self.next(today)? - today).num_days())
    }

    fn years(&self, today: NaiveDate) -> Option<i32> {
        Some(self.next(today)?.year() - self.base()?.year())
    }
}

fn data_file() -> PathBuf {
    DATA_DIR
        .get()
        .cloned()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("dates.json")
}

fn load_items() -> Vec<Entry> {
    let Ok(text) = std::fs::read_to_string(data_file()) else {
        return Vec::new();
    };
    let Ok(value) = serde_json::from_str::<serde_json::Value>(&text) else {
        return Vec::new();
    };
    let arr = value.get("items").cloned().unwrap_or(value);
    serde_json::from_value::<Vec<Entry>>(arr).unwrap_or_default()
}

fn save_items(items: &[Entry]) {
    let json = serde_json::json!({ "app": "dates", "items": items });
    let _ = std::fs::write(data_file(), json.to_string());
}

fn hex(v: u32) -> Color32 {
    Color32::from_rgb((v >> 16) as u8, (v >> 8) as u8, v as u8)
}

struct Palette {
    bg: Color32,
    card: Color32,
    ink: Color32,
    muted: Color32,
    accent: Color32,
    highlight: Color32,
}

const THEME_NAMES: [&str; 6] = ["Cream", "Wine", "Midnight", "Forest", "Lavender", "Noir"];

fn palette(i: usize) -> Palette {
    match i {
        1 => Palette { bg: hex(0x1a1214), card: hex(0x2a1f22), ink: hex(0xffffff), muted: hex(0xb59ea3), accent: hex(0x8b1e33), highlight: hex(0xe8a2ab) },
        2 => Palette { bg: hex(0x0f1626), card: hex(0x1a2438), ink: hex(0xeef2ff), muted: hex(0x8fa0c2), accent: hex(0x3d5fd9), highlight: hex(0x9bb8ff) },
        3 => Palette { bg: hex(0xecf2ea), card: hex(0xf8fbf6), ink: hex(0x203025), muted: hex(0x6d7f70), accent: hex(0x3f7d57), highlight: hex(0x3f6b4a) },
        4 => Palette { bg: hex(0xf4f0fa), card: hex(0xfdfbff), ink: hex(0x2e2640), muted: hex(0x7d7291), accent: hex(0x8a5bd1), highlight: hex(0x7b4fb3) },
        5 => Palette { bg: hex(0x121212), card: hex(0x1e1e1e), ink: hex(0xf2f2f2), muted: hex(0x9a9a9a), accent: hex(0x3a3a3a), highlight: hex(0xd4d4d4) },
        _ => Palette { bg: hex(0xf7f1e8), card: hex(0xfffaf3), ink: hex(0x3a2e28), muted: hex(0x8c7b70), accent: hex(0xc2614f), highlight: hex(0xa84b44) },
    }
}

#[derive(Clone)]
struct Draft {
    id: Option<String>,
    name: String,
    kind: String,
    date: String,
    remind: i64,
    rel: String,
}

struct DatesApp {
    items: Vec<Entry>,
    theme: usize,
    draft: Option<Draft>,
}

impl DatesApp {
    fn open_new(&mut self) {
        self.draft = Some(Draft {
            id: None,
            name: String::new(),
            kind: "birthday".to_string(),
            date: Local::now().date_naive().format("%Y-%m-%d").to_string(),
            remind: default_remind(),
            rel: String::new(),
        });
    }

    fn open_edit(&mut self, id: &str) {
        if let Some(e) = self.items.iter().find(|e| e.id == id) {
            self.draft = Some(Draft {
                id: Some(e.id.clone()),
                name: e.name.clone(),
                kind: e.kind.clone(),
                date: e.date.clone(),
                remind: e.remind,
                rel: e.rel.clone(),
            });
        }
    }

    fn commit(&mut self, d: &Draft) {
        let entry = Entry {
            id: d.id.clone().unwrap_or_else(|| Utc::now().timestamp_millis().to_string()),
            name: d.name.trim().to_string(),
            kind: d.kind.clone(),
            date: d.date.trim().to_string(),
            remind: d.remind,
            rel: d.rel.trim().to_string(),
        };
        match self.items.iter().position(|e| e.id == entry.id) {
            Some(i) => self.items[i] = entry,
            None => self.items.push(entry),
        }
        save_items(&self.items);
    }

    fn draft_window(&mut self, ctx: &egui::Context) {
        let Some(mut d) = self.draft.take() else {
            return;
        };
        let mut keep = true;
        let mut save = false;
        let mut delete = false;
        let valid_date = NaiveDate::parse_from_str(d.date.trim(), "%Y-%m-%d").is_ok();
        let title = if d.id.is_some() { "Edit date" } else { "New date" };

        egui::Window::new(title)
            .collapsible(false)
            .resizable(false)
            .anchor(Align2::CENTER_CENTER, [0.0, 0.0])
            .show(ctx, |ui| {
                ui.label("Name");
                ui.text_edit_singleline(&mut d.name);
                ui.horizontal(|ui| {
                    ui.radio_value(&mut d.kind, "birthday".to_string(), "Birthday");
                    ui.radio_value(&mut d.kind, "anniversary".to_string(), "Anniversary");
                });
                ui.label("Date (YYYY-MM-DD)");
                ui.text_edit_singleline(&mut d.date);
                ui.horizontal(|ui| {
                    ui.label("Remind before (days)");
                    ui.add(egui::DragValue::new(&mut d.remind).range(0..=60));
                });
                ui.label("Relationship (optional)");
                ui.text_edit_singleline(&mut d.rel);
                ui.horizontal(|ui| {
                    let can_save = !d.name.trim().is_empty() && valid_date;
                    if ui.add_enabled(can_save, egui::Button::new("Save")).clicked() {
                        save = true;
                    }
                    if d.id.is_some() && ui.button("Delete").clicked() {
                        delete = true;
                    }
                    if ui.button("Cancel").clicked() {
                        keep = false;
                    }
                });
            });

        if save {
            self.commit(&d);
            keep = false;
        }
        if delete {
            if let Some(id) = &d.id {
                self.items.retain(|e| &e.id != id);
                save_items(&self.items);
            }
            keep = false;
        }
        if keep {
            self.draft = Some(d);
        }
    }
}

fn countdown(days: i64) -> String {
    match days {
        0 => "Today".to_string(),
        1 => "Tomorrow".to_string(),
        n => format!("in {n} days"),
    }
}

impl eframe::App for DatesApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        let p = palette(self.theme);
        let today = Local::now().date_naive();
        let mut order = self.items.clone();
        order.sort_by_key(|e| e.days(today).unwrap_or(i64::MAX));
        let mut tapped: Option<String> = None;

        egui::CentralPanel::default()
            .frame(egui::Frame::none().fill(p.bg).inner_margin(16.0))
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    ui.label(RichText::new("Dates").size(30.0).strong().color(p.highlight));
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if ui.button("+ Add").clicked() {
                            self.open_new();
                        }
                        egui::ComboBox::from_id_salt("theme")
                            .selected_text(THEME_NAMES[self.theme])
                            .show_ui(ui, |ui| {
                                for (i, name) in THEME_NAMES.iter().enumerate() {
                                    ui.selectable_value(&mut self.theme, i, *name);
                                }
                            });
                    });
                });
                ui.add_space(12.0);

                if order.is_empty() {
                    ui.label(RichText::new("No dates yet. Tap + Add to add a birthday or anniversary.").color(p.muted));
                }

                egui::ScrollArea::vertical().show(ui, |ui| {
                    for e in &order {
                        let days = e.days(today).unwrap_or(0);
                        let years = e.years(today).unwrap_or(0);
                        let kind = if e.kind == "anniversary" { "Anniversary" } else { "Birthday" };
                        let detail = if e.kind == "anniversary" {
                            format!("{years} yr{}", if years == 1 { "" } else { "s" })
                        } else {
                            format!("turns {years}")
                        };
                        let frame = egui::Frame::none()
                            .fill(p.card)
                            .rounding(14.0)
                            .inner_margin(14.0);
                        let response = frame
                            .show(ui, |ui| {
                                ui.set_width(ui.available_width());
                                ui.horizontal(|ui| {
                                    ui.vertical(|ui| {
                                        ui.label(RichText::new(&e.name).size(17.0).strong().color(p.ink));
                                        ui.label(RichText::new(format!("{kind} · {detail}")).size(13.0).color(p.muted));
                                    });
                                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                                        let color = if days == 0 { p.accent } else { p.highlight };
                                        ui.label(RichText::new(countdown(days)).strong().color(color));
                                    });
                                });
                            })
                            .response;
                        let click = ui.interact(response.rect, ui.id().with(&e.id), egui::Sense::click());
                        if click.clicked() {
                            tapped = Some(e.id.clone());
                        }
                        ui.add_space(10.0);
                    }
                });
            });

        if let Some(id) = tapped {
            self.open_edit(&id);
        }
        self.draft_window(ctx);
    }
}

#[cfg(target_os = "android")]
#[no_mangle]
fn android_main(app: winit::platform::android::activity::AndroidApp) {
    use winit::platform::android::EventLoopBuilderExtAndroid;

    if let Some(dir) = app.internal_data_path() {
        let _ = DATA_DIR.set(dir);
    }
    let mut options = eframe::NativeOptions::default();
    options.event_loop_builder = Some(Box::new(move |builder| {
        builder.with_android_app(app);
    }));
    let items = load_items();
    eframe::run_native(
        "Dates",
        options,
        Box::new(move |_cc| {
            Ok(Box::new(DatesApp { items: items.clone(), theme: 0, draft: None }) as Box<dyn eframe::App>)
        }),
    )
    .unwrap();
}

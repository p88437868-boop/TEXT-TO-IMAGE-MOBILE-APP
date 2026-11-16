"""Dark theme matching DESIGN.md (canvas #0B0B10, cards #1E1E23, accent #9B6DDB)."""

from __future__ import annotations

COLORS = {
    "canvas": "#0B0B10",
    "surface": "#1E1E23",
    "surface_input": "#141418",
    "border": "#2E2E35",
    "accent": "#9B6DDB",
    "accent_pressed": "#8456C8",
    "text": "#FFFFFF",
    "text_secondary": "#B4B4BD",
    "text_muted": "#80808A",
    "success": "#3FB950",
    "warning": "#E3B341",
    "danger": "#E5484D",
}

STYLESHEET = f"""
* {{
    font-family: "Segoe UI", "Inter", sans-serif;
    font-size: 13px;
    color: {COLORS['text']};
}}
QWidget {{
    background-color: {COLORS['canvas']};
}}
QLabel#Title {{ font-size: 20px; font-weight: 600; }}
QLabel#Subtitle {{ color: {COLORS['text_secondary']}; font-size: 12px; }}
QLabel#Section {{ color: {COLORS['text_secondary']}; font-size: 11px; font-weight: 600;
                  text-transform: uppercase; letter-spacing: 1px; }}
QLabel#Muted {{ color: {COLORS['text_muted']}; font-size: 11px; }}
QLabel#Banner {{ color: {COLORS['text_secondary']}; font-size: 12px; }}

QFrame#Card {{
    background-color: {COLORS['surface']};
    border: 1px solid {COLORS['border']};
    border-radius: 14px;
}}
QFrame#StatCard {{
    background-color: {COLORS['surface']};
    border: 1px solid {COLORS['border']};
    border-radius: 12px;
}}

QPushButton {{
    background-color: {COLORS['surface_input']};
    border: 1px solid {COLORS['border']};
    border-radius: 9px;
    padding: 8px 14px;
}}
QPushButton:hover {{ border-color: {COLORS['accent']}; }}
QPushButton:disabled {{ color: {COLORS['text_muted']}; border-color: {COLORS['border']}; }}
QPushButton#Primary {{
    background-color: {COLORS['accent']};
    border: none;
    font-weight: 600;
}}
QPushButton#Primary:hover {{ background-color: {COLORS['accent_pressed']}; }}
QPushButton#Danger {{ color: {COLORS['danger']}; }}
QPushButton#Danger:hover {{ border-color: {COLORS['danger']}; }}

QLineEdit, QPlainTextEdit, QSpinBox, QComboBox, QDateEdit {{
    background-color: {COLORS['surface_input']};
    border: 1px solid {COLORS['border']};
    border-radius: 9px;
    padding: 7px 10px;
    selection-background-color: {COLORS['accent']};
}}
QLineEdit:focus, QPlainTextEdit:focus, QSpinBox:focus, QComboBox:focus, QDateEdit:focus {{
    border-color: {COLORS['accent']};
}}
QLineEdit#KeyField, QPlainTextEdit#KeyField {{
    font-family: "Consolas", monospace;
    font-size: 14px;
}}

QTableWidget {{
    background-color: {COLORS['surface']};
    border: 1px solid {COLORS['border']};
    border-radius: 12px;
    gridline-color: {COLORS['border']};
    selection-background-color: #2A2438;
    selection-color: {COLORS['text']};
}}
QHeaderView::section {{
    background-color: {COLORS['surface']};
    color: {COLORS['text_secondary']};
    border: none;
    border-bottom: 1px solid {COLORS['border']};
    padding: 8px;
    font-size: 11px;
    font-weight: 600;
}}
QTableWidget::item {{ padding: 4px; }}

QTabWidget::pane {{ border: 1px solid {COLORS['border']}; border-radius: 12px; top: -1px; }}
QTabBar::tab {{
    background: transparent;
    color: {COLORS['text_muted']};
    padding: 9px 18px;
    border: none;
}}
QTabBar::tab:selected {{ color: {COLORS['text']}; font-weight: 600; }}
QTabBar::tab:hover {{ color: {COLORS['text_secondary']}; }}

QScrollArea {{ border: none; }}
QScrollBar:vertical {{ background: transparent; width: 10px; margin: 2px; }}
QScrollBar::handle:vertical {{ background: {COLORS['border']}; border-radius: 5px; min-height: 24px; }}
QScrollBar::add-line, QScrollBar::sub-line {{ height: 0; }}
QStatusBar {{ color: {COLORS['text_muted']}; }}
QCheckBox {{ color: {COLORS['text_secondary']}; }}
QToolTip {{
    background-color: {COLORS['surface']};
    color: {COLORS['text']};
    border: 1px solid {COLORS['border']};
    padding: 6px;
}}
QMessageBox {{ background-color: {COLORS['canvas']}; }}
"""

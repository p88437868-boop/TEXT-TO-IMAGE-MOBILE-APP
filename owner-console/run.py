"""Double-click launcher during development (PyInstaller builds its own exe)."""

import sys

from textpic_console.app import main

if __name__ == "__main__":
    sys.exit(main())

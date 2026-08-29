# IISViewer

![Development status](https://img.shields.io/badge/status-LTS-blue)

**IISViewer** — a client-side Minecraft mod and add-on for [**UltimateImprovments**](https://github.com/rizer001-Development/UltimateImprovments) that makes it easy to see the integrity of items.

---

### 📄 Organization Docs

[![Guide](https://img.shields.io/badge/Guide-rizer001--Development-00AEFF)](https://github.com/rizer001-Development/.github/blob/main/GUIDE.md) · [![Contributing](https://img.shields.io/badge/Contributing-rizer001--Development-4CAF50)](https://github.com/rizer001-Development/.github/blob/main/CONTRIBUTING.md) · [![Security](https://img.shields.io/badge/Security-rizer001--Development-D9534F)](https://github.com/rizer001-Development/.github/blob/main/SECURITY.md) · [![Code of Conduct](https://img.shields.io/badge/Code%20of%20Conduct-rizer001--Development-5BC0DE)](https://github.com/rizer001-Development/.github/blob/main/CODE_OF_CONDUCT.md)

## Features

- **Item integrity HUD overlay** — shows the integrity of items directly in the game HUD
- **Integrity data** — reads the item integrity values provided by the UltimateImprovments plugin
- **Client-side only** — no server mod required, works as a companion to the UltimateImprovments server plugin
- **Configurable** — settings via the mod config (`ModConfig`)

## Project structure

```
IISViewer/
├── src/main/java/com/iisviewer/
│   ├── IISViewerMod.java          — Mod entry point
│   ├── IntegrityData.java         — Item integrity data model
│   ├── IntegrityHudOverlay.java   — HUD overlay rendering
│   ├── ModConfig.java             — Mod configuration
│   └── mixin/IISViewerMixin.java  — Mixin hooks
├── run/                           — Dev environment (Fabric)
└── build.gradle                   — Fabric mod build
```

## License

This project is licensed under the **GNU Affero General Public License v3.0**.  
See the [LICENSE](LICENSE) file for details.

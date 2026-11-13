# Repository guidance

- Use OpenSpec for current requirements and changes. Follow the selected change's scope and tasks.
- `docs/archive/YYYYmmdd-<module>/` contains frozen historical module documents. Do not read it for routine work or edit it during ordinary implementation. Read a specific archived file only when tracing historical decisions.
- Do not update or regenerate `docs/archive/manifest.json` to make a changed archive pass. Corrections require an explicitly scoped change that preserves provenance and the original evidence.
- Run `api/scripts/tools/check-maintenance.ps1` to verify archive provenance, frozen hashes, and README links.

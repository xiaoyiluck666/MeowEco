# Versioning Policy

MeowEco maintains two independently updated release lines.

## Modern line

The `main` branch uses three numeric components:

```text
26.10.8
```

Modern release tags use the same version with a `v` prefix, for example
`v26.10.8`.

## Paper 1.21.11 LTS line

The `legacy/1.21.x` branch uses the modern baseline, an independent LTS
revision, and the target Minecraft suffix:

```text
26.10.7.1-mc1.21.11
26.10.7.2-mc1.21.11
```

The first three components identify the modern release used as the LTS
baseline. The fourth component increments for every LTS-only release. The
baseline changes only when the branch deliberately synchronizes with a newer
modern milestone.

LTS release tags preserve the full version, for example
`v26.10.7.1-mc1.21.11`. Modrinth displays the same release as
`MeowEco 26.10.7.1 - Paper 1.21.11 LTS`.

Do not use build metadata such as `+build.1` for public releases because some
update systems ignore it during version comparison.

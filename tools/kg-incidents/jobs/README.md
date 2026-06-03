# Zbundlowany przebieg referencyjny — 2026-04-17

Ten katalog normalnie jest gitignorowany (artefakty per-run). **Wyjątkiem jest
jeden wyselekcjonowany przebieg z 2026-04-17**, zacommitowany celowo: żeby
każdy mógł obejrzeć wyniki w przeglądarce Harbor **bez ponownego uruchamiania
benchmarku i bez palenia własnych tokenów**.

To są dokładnie te triale, na których oparty jest [`../REPORT-2026-04-17.md`](../REPORT-2026-04-17.md).

## Co tu jest

- **Scenariusz:** `cascading-shared-dependency`
- **Model:** `claude-opus-4-6`
- **Warianty:** `with-mcp` (4 MCP domenowe) vs `with-mcp-kg` (4 MCP + `aj-knowledge-graph`)
- **N = 3 udane triale per wariant** (reward = 1.0 we wszystkich 6)

| Job | Wariant | input tok | output tok | reward |
|---|---|---|---|---|
| `2026-04-17__15-24-52__with-mcp-kg__0e3acc` | with-mcp-kg | 956 820 | 23 796 | 1.0 |
| `2026-04-17__18-24-47__with-mcp-kg__eba3b5` | with-mcp-kg | 1 047 816 | 26 799 | 1.0 |
| `2026-04-17__18-37-49__with-mcp-kg__bdea94` | with-mcp-kg | 797 963 | 15 003 | 1.0 |
| `2026-04-17__15-30-31__with-mcp__ed6fbe` | with-mcp | 1 337 390 | 29 845 | 1.0 |
| `2026-04-17__18-24-45__with-mcp__6276c9` | with-mcp | 1 499 982 | 33 570 | 1.0 |
| `2026-04-17__18-41-24__with-mcp__52c7bf` | with-mcp | 1 604 060 | 46 652 | 1.0 |

## Przewaga grafu wiedzy (średnie, N=3)

| Metryka | with-mcp-kg (KG) | with-mcp (bez grafu) | Pogorszenie bez grafu |
|---|---|---|---|
| **Czas wykonania zadania** | **130 s** | 302 s | **+132% wolniej** |
| **Tokeny wejściowe** | **934 200** | 1 480 477 | **+58% więcej** |
| **Tokeny wyjściowe** | **21 866** | 36 689 | **+68% więcej** |
| **Liczba wywołań narzędzi** | **26,7** | 36,7 | **+37% więcej** |

> Czas to faza pracy agenta (`agent_execution`) — tak samo jak w
> [`../REPORT-2026-04-17.md`](../REPORT-2026-04-17.md). Oba warianty
> rozwiązały scenariusz w każdym trialu (6/6 reward = 1.0); różnica leży
> w **efektywności**, nie w samej zdolności. Agent wyposażony w graf wykonuje
> tę samą pracę ponad dwukrotnie szybciej, zużywając ułamek zasobów.

## Jak przeglądać (bez uruchamiania benchmarku)

Wymagane jednorazowo CLI NASDE — instalacja w [`../README.md`](../README.md) §1.
Samo przeglądanie **nie potrzebuje** tokenu Claude ani Dockera; Harbor czyta
gotowe artefakty z dysku.

```bash
cd tools/kg-incidents
nasde harbor view jobs/2026-04-17__18-37-49__with-mcp-kg__bdea94
```

Podmień nazwę joba na dowolny z tabeli powyżej, żeby porównać warianty.
Surowe pliki, jeśli wolisz grepować bezpośrednio:

```bash
cat jobs/2026-04-17__18-37-49__with-mcp-kg__bdea94/cascading-shared-dependency__*/result.json | python3 -m json.tool
cat jobs/2026-04-17__18-37-49__with-mcp-kg__bdea94/cascading-shared-dependency__*/agent/trajectory.json | python3 -m json.tool
cat jobs/2026-04-17__18-37-49__with-mcp-kg__bdea94/cascading-shared-dependency__*/verifier/test-stdout.txt
```

## Czego tu NIE ma

Z każdego triala usunięto:

- `artifacts/workspace/` — pełny snapshot repozytorium po przebiegu
  (~9,4 MB/trial). Zostaje `artifacts/manifest.json` — lista plików,
  które wtedy były w workspace.
- `agent/sessions/` — wewnętrzny stan runtime Claude Code (kopie
  `~/.claude.json`, logi sesji). To nie są wyniki ewaluacji.

`harbor view` żadnego z nich nie potrzebuje — trajektoria
(`agent/trajectory.json`), wynik (`result.json`), reward i wyjście
weryfikatora są zachowane osobno.

Cały zbundlowany przebieg to ~5,2 MB zamiast ~64 MB.

Aby odtworzyć pełne wyniki (z workspace) od zera — zobacz [`../README.md`](../README.md).

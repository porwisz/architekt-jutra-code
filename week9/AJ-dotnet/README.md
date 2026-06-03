# AJ-dotnet-with-arch-2

## Wymagania wstępne

- Zainstalowany [Claude Code](https://claude.com/claude-code)
- [Git](https://git-scm.com/)

## Konfiguracja

### 1. Sklonuj wtyczkę Noesis SDLC

Sklonuj repozytorium wtyczki do wybranej lokalizacji:

```bash
git clone https://github.com/NoesisVision/SDLC.git
```

### 2. Uruchom Claude Code z katalogiem wtyczki

Uruchom Claude Code z tego projektu, wskazując parametrem `--plugin-dir` na sklonowane repozytorium SDLC:

```bash
claude --plugin-dir /sciezka/do/SDLC
```

Zastąp `/sciezka/do/SDLC` bezwzględną ścieżką do katalogu, w którym sklonowano repozytorium SDLC w kroku 1.

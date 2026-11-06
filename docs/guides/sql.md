# SQL

Each release has the catalog as `polyglossa.sql`, plain SQL that SQLite, PostgreSQL and MySQL all read, for
programs in any language:

```bash
gh release download {{ version }} --repo fenakhay/polyglossa --pattern polyglossa.sql
sqlite3 polyglossa.sqlite < polyglossa.sql
```

`./gradlew sqlDump` writes the same file from a checkout, to `polyglossa-tools/build/sql/polyglossa.sql`.

## Tables

Every list a language, a family or a script holds is a table of its own, with a `position` that keeps
its order.

| Table | Columns |
|---|---|
| `metadata` | `key`, `value`: the format (`1`) and the date the data was exported |
| `languages` | `code`, `name`, `type`, `wikidata`, `family`, `parent` |
| `language_scripts` | `language`, `position`, `script`: most usual first |
| `language_ancestors` | `language`, `position`, `ancestor` |
| `language_aliases` | `language`, `position`, `alias` |
| `varieties` | `language`, `position`, `name` |
| `variety_other_names` | `language`, `variety`, `position`, `name` |
| `families` | `code`, `name`, `wikidata`, `parent`, `proto_language` |
| `family_aliases` | `family`, `position`, `alias` |
| `scripts` | `code`, `name`, `wikidata`, `system`, `parent`, `direction` |
| `script_aliases` | `script`, `position`, `alias` |
| `script_local_names` | `script`, `language`, `name` |
| `script_ranges` | `script`, `first`, `last`: its characters, as code points |
| `script_marks` | `script`, `first`, `last`: the characters that tell Traditional from Simplified Han |

`languages.type` is `regular`, `reconstructed`, `appendix-constructed` or `etymology-only`, and
`scripts.direction` is `ltr`, `rtl`, `vertical-ltr` or `vertical-rtl`.

## Queries

The scripts of Arabic, most usual first:

```sql
SELECT s.name FROM language_scripts ls JOIN scripts s ON s.code = ls.script
WHERE ls.language = 'ar' ORDER BY ls.position;
```

The scripts that hold a character, here U+0416, Ж:

```sql
SELECT script FROM script_ranges WHERE 1046 BETWEEN first AND last;   -- Cyrl, Cyrs
```

Every language of the Germanic family, at every depth:

```sql
WITH RECURSIVE germanic(code) AS (
    SELECT 'gem'
    UNION ALL
    SELECT f.code FROM families f JOIN germanic g ON f.parent = g.code
)
SELECT l.code, l.name FROM languages l WHERE l.family IN (SELECT code FROM germanic) ORDER BY l.name;
```

A language's ancestors, as far back as Wiktionary records them:

```sql
WITH RECURSIVE line(code, depth) AS (
    SELECT 'en', 0
    UNION ALL
    SELECT a.ancestor, line.depth + 1 FROM language_ancestors a JOIN line ON a.language = line.code
    WHERE a.position = 0
)
SELECT l.name FROM line JOIN languages l ON l.code = line.code ORDER BY depth;
```

The SQL has the data, not the logic: script detection needs one of the libraries.

# SQLite From Scratch (Java)

A SQLite file reader and query engine written from scratch in Java — no
`org.xerial:sqlite-jdbc`, no JNI bindings to `libsqlite3`. It parses the
[SQLite file format](https://www.sqlite.org/fileformat.html) directly off
disk, walks the on-disk B-trees itself, and evaluates a useful subset of SQL
`SELECT` against the result.

Originally started as a solution to CodeCrafters'
["Build Your Own SQLite"](https://codecrafters.io/challenges/sqlite)
challenge, this has grown past the scaffolding into a small standalone
database engine.

## What it can do

- **Parse the 100-byte database header** — page size, format versions, text
  encoding, page/freelist counts, schema cookie, application ID, etc.
  (`.dbinfo`)
- **Read the `sqlite_schema` table** to discover every table and index, and
  extract column names straight out of the stored `CREATE TABLE` /
  `CREATE INDEX` SQL.
- **Walk B-tree pages** — interior and leaf pages, for both table b-trees
  (keyed by rowid) and index b-trees — including multi-level trees, not just
  single-page ones.
- **Decode SQLite's record format**: varint cell/payload/header lengths and
  the full serial-type space (`NULL`, 8/16/24/32/48/64-bit signed integers,
  IEEE-754 doubles, the `0`/`1` boolean constants, `BLOB`, and `TEXT` in
  UTF-8, UTF-16LE, or UTF-16BE).
- **Execute `SELECT` queries**:
  - `SELECT * FROM table`
  - `SELECT col1, col2 FROM table`
  - `SELECT COUNT(*) FROM table`
  - `SELECT ... FROM table WHERE column = 'value'`
- **Use an index automatically** — if a single-column index exists on the
  filtered column, the engine performs an index seek (b-tree walk + rowid
  lookup) instead of a full table scan.

Out of scope (by design, not yet on the roadmap): joins, `ORDER BY` /
`GROUP BY`, multiple `WHERE` conditions, writes (`INSERT`/`UPDATE`/`DELETE`),
and anything requiring the SQLite write-ahead log or freelist management —
this is a **read-only** engine.

## How it's built

```
src/main/java/sqlite/
├── Main.java                    entry point: .dbinfo / .tables / SELECT
├── SQLiteParser.java            header, page, cell, and record decoding
├── domain/
│   ├── Database.java            header + schema + open file channel
│   ├── DatabaseHeader.java      the 100-byte header, fully decoded
│   ├── Page.java / PageHeader.java / PageType.java   b-tree page framing
│   ├── Cell.java                 sealed hierarchy: interior/leaf × table/index
│   ├── TableRow.java / IndexRow.java / Row.java       decoded records
│   ├── TextEncoding.java
│   ├── type/                    serial-type decoders (Integer, Double,
│   │                             Boolean, Blob, String, Null)
│   ├── schema/                  Schema / Table / Index + CREATE-statement
│   │                             SQL parsing
│   └── query/                   b-tree iterators, WHERE predicates, and
│       ├── LeafTableIterator     the Query implementations
│       ├── LeafIndexIterator
│       └── impl/{ReadQuery,CountQuery}
└── util/BufferUtils.java        varint decoding, unsigned reads, etc.
```

The design mirrors the file format itself: `SQLiteParser` turns bytes into
typed records (`DatabaseHeader`, `Page`, `Cell`, `TableRow`, ...), `Schema`
gives query code a way to resolve table/index names to root pages and column
lists, and `LeafTableIterator` / `LeafIndexIterator` lazily walk the b-tree
(interior pages are expanded on demand, not loaded eagerly) so scans and
index seeks work the same way SQLite itself performs them, page by page.

## Requirements

- Java 21+
- Maven

## Building & running

```sh
./your_sqlite3.sh <path-to-database> <command>
```

This script builds the project with Maven and runs the resulting jar — it's
the fastest way to try things out. Equivalently:

```sh
mvn -B --quiet package -Ddir=target
java -jar target/java_sqlite.jar <path-to-database> <command>
```

### Inspect a database

```sh
$ ./your_sqlite3.sh sample.db .dbinfo
database page size:  4096
write format:        1
read format:         1
reserved bytes:      0
...
number of tables:    2

$ ./your_sqlite3.sh sample.db .tables
apples oranges
```

### Run queries

```sh
$ ./your_sqlite3.sh sample.db "SELECT COUNT(*) FROM apples"
4

$ ./your_sqlite3.sh sample.db "SELECT id, name FROM apples"
1|Granny Smith
2|Fuji
3|Honeycrisp
4|Golden Delicious

$ ./your_sqlite3.sh sample.db "SELECT name FROM apples WHERE color = 'Yellow'"
Golden Delicious
```

## Tests

Unit tests cover the trickier binary-parsing logic (varint decoding across
its full 1–9 byte range, including the unsigned 64-bit edge case):

```sh
mvn test
```

## Sample databases

`sample.db` ships in the repo (two tables: `apples`, `oranges`) so you can
try things immediately. Two larger fixtures used for scale/index testing
aren't checked in — fetch them with:

```sh
./download_sample_databases.sh
```

- `superheroes.db` (~1MB) — one large table, good for table-scan testing.
- `companies.db` (~7MB) — one table plus `idx_companies_country`, good for
  exercising the index-seek path.

If the script fails, grab them directly from
[codecrafters-io/sample-sqlite-databases](https://github.com/codecrafters-io/sample-sqlite-databases).

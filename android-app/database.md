# Database

Room (SQLite) on the device. No server database.

## Table `readings`
| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | INTEGER PK autoincrement | no | |
| `sample_id` | TEXT | no | indexed |
| `device_time` | TEXT | no | `timestamp` from payload (ISO-8601) |
| `received_at` | INTEGER | no | phone epoch ms; used for ordering because the device clock reads 1970 when NTP is unsynced |
| `delta_e` | REAL | no | |
| `r`, `g`, `b` | INTEGER | no | 0–255 |
| `lab_l`, `lab_a`, `lab_b` | REAL | no | |
| `ambient_temp` | REAL | yes | |
| `humidity` | REAL | yes | |
| `light_lux` | INTEGER | yes | |
| `bath_temp` | REAL | yes | null = sensor disconnected |
| `bath_alert` | INTEGER (bool) | no | |
| `setpoint_c` | REAL | yes | |
| `fabric`, `dye` | TEXT | yes | |
| `dye_conc` | REAL | yes | |
| `target_l`, `target_a`, `target_b` | REAL | yes | firmware target Lab |
| `target_set` | INTEGER (bool) | no | |

Indexes: `(sample_id, received_at)` for per-sample queries, `(received_at)` for the latest-row query.

## Samples list query
```sql
SELECT r.sample_id,
       MAX(r.received_at) AS last_at,
       COUNT(*)           AS n,
       MAX(r.fabric)      AS fabric,
       MAX(r.dye)         AS dye,
       (SELECT delta_e FROM readings r2
         WHERE r2.sample_id = r.sample_id
         ORDER BY received_at DESC LIMIT 1) AS last_delta_e
FROM readings r
GROUP BY r.sample_id
ORDER BY last_at DESC;
```

## DAO sketch
```kotlin
@Dao
interface ReadingDao {
    @Insert suspend fun insert(r: ReadingEntity)

    @Query("SELECT * FROM readings ORDER BY received_at DESC LIMIT 1")
    fun latest(): Flow<ReadingEntity?>

    @Query("SELECT * FROM readings WHERE sample_id = :id ORDER BY received_at DESC LIMIT :n")
    fun trend(id: String, n: Int): Flow<List<ReadingEntity>>

    @Query("DELETE FROM readings WHERE sample_id = :id")
    suspend fun deleteSample(id: String)
}
```

## Retention
- Keep all readings by default. At one reading per 3 s for 8 h/day: about 9,600 rows/day, roughly 1–2 MB/day.
- Setting: auto-delete readings older than N days (default off). Not built yet.

## Migrations
- `exportSchema = true`; schema JSON committed under `app/schemas/`.
- Every schema change ships a `Migration`. No `fallbackToDestructiveMigration()` in release builds.

## Export
CSV columns match `FIELDS` in `logger.py` (same format as `trials_real.csv`). `sensor_readings.csv` is a synthetic bath log with different columns, so it is not the model. `bath_alert` is written as `True`/`False`, as Python's csv module does.

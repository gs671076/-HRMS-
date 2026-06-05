# HRMS — Worker Attendance & Overtime Settlement Engine

Forked from [amigoscode/spring-boot-fullstack-professional](https://github.com/amigoscode/spring-boot-fullstack-professional) — clean Spring Boot + JPA + PostgreSQL structure, easy to extend without rewriting from scratch.

---

## Stack
- Java 17 + Spring Boot 3.2
- Hibernate/JPA with Supabase (PostgreSQL via PgBouncer)
- Redis for active worker caching
- Spring Security for CORS + API security

---

## Setup

### 1. Supabase
1. Create a project at [supabase.com](https://supabase.com)
2. Go to **Settings → Database → Connection string**
3. Select **Connection pooling** tab — copy the **Transaction mode** URL (port **6543**)
   > ⚠️ Use port 6543 (PgBouncer), NOT 5432 (direct). Direct connections exhaust the free-tier limit under load.

### 2. Redis
Local: `docker run -d -p 6379:6379 redis`
Cloud: any free Redis instance (Upstash, Redis Cloud)

### 3. Environment variables
```bash
export SUPABASE_HOST=db.xxxxxxxxxxxx.supabase.co
export SUPABASE_PASSWORD=your_db_password
export REDIS_HOST=localhost   # or your Redis host
export REDIS_PORT=6379
```

### 4. Run
```bash
# Local
mvn spring-boot:run

# Staging profile (HikariCP tuned for Supabase)
mvn spring-boot:run -Dspring.profiles.active=staging
```

---

## API Endpoints

### Attendance

**Clock in**
```bash
curl -X POST http://localhost:8080/api/attendance/clock-in \
  -H "Content-Type: application/json" \
  -d '{"workerId": 1, "siteId": 1}'
```

**Clock out**
```bash
curl -X POST http://localhost:8080/api/attendance/clock-out \
  -H "Content-Type: application/json" \
  -d '{"workerId": 1}'
```

**Active workers (Redis)**
```bash
curl http://localhost:8080/api/attendance/active
```

**Attendance log (paginated)**
```bash
curl "http://localhost:8080/api/attendance/log?workerId=1&from=2026-05-01&to=2026-05-31&page=0&size=20"
```

### Overtime

**Monthly summary**
```bash
curl "http://localhost:8080/api/overtime/summary/1?month=2026-05"
```

**Settle (past months only)**
```bash
curl -X POST "http://localhost:8080/api/overtime/settle/1?month=2026-04"
```

### Workers

```bash
# Create worker
curl -X POST http://localhost:8080/api/workers \
  -H "Content-Type: application/json" \
  -d '{"name":"Ramesh Kumar","phone":"9876543210","designation":"MASON","dailyWageRate":600}'

# Get all
curl http://localhost:8080/api/workers

# Update (triggers Redis cache eviction)
curl -X PUT http://localhost:8080/api/workers/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Ramesh Kumar","phone":"9876543210","designation":"SUPERVISOR","dailyWageRate":800}'
```

---

## Error Response Format

All errors return structured JSON:
```json
{
  "error": "DUPLICATE_CLOCK_IN",
  "message": "Worker is already clocked in at Site: Greenfield Phase 2",
  "timestamp": "2026-05-25T10:30:00Z"
}
```

HTTP codes: `400` validation · `404` not found · `409` conflict (duplicate clock-in, already settled) · `422` settlement of current month

---

## Ticket Fixes

| Ticket | Root cause | Fix |
|--------|-----------|-----|
| **LF-201** | Spring Security rejected OPTIONS preflight before `@CrossOrigin` ran | `CorsConfigurationSource` bean registered in `SecurityFilterChain` before `.csrf()`. Origins in `application.yml` per-environment. |
| **LF-202** | App crashed on boot if Redis unavailable | `CacheErrorHandler` swallows all cache exceptions. `connect-timeout: 2000ms`. App starts and serves from DB when Redis is down. |
| **LF-203** | `findAll()` with no pagination + N+1 per Worker/Site | `@EntityGraph(attributePaths={"worker","site"})` on repo method. `Pageable` parameter. Returns `PagedAttendanceResponse` wrapper. |
| **LF-204** | Per-entry commit loop + SMS sent inside transaction | Single `@Transactional` wraps entire settle batch. `OvertimeSettledEvent` published and consumed by `@TransactionalEventListener(AFTER_COMMIT)`. |
| **LF-205** | Supabase killed idle connections; external API held DB connection inside `@Transactional` | `max-lifetime=270s`, `keepalive-time=120s`, PgBouncer port 6543. External API call moved before transaction opens. |

---

## Design Decisions

**Why Redis TTL = 57600s (16 hours)?**
16 hours is the maximum possible shift. If a clock-out is missed (phone died, supervisor forgot), the Redis entry auto-expires instead of showing a worker as permanently active. The attendance record is flagged in the DB for manual review.

**Why 60-hour monthly overtime cap?**
Cost control constraint from the client. The cap is enforced at clock-out time by querying the month's already-accumulated OT hours and capping the new entry — the attendance record itself is never modified.

**Why external API outside @Transactional (LF-205)?**
A DB connection is checked out from HikariCP the moment `@Transactional` opens. If an HTTP call to a government API takes 3-5 seconds inside the transaction, that connection sits idle for those seconds. With 10 connections and 20 concurrent users, the math fails. Fetching the data before opening the transaction means connections are held only for actual DB work.

**Why AFTER_COMMIT for SMS (LF-204)?**
A worker who receives "Your March overtime of ₹4,200 is settled" and doesn't get paid is a trust and legal problem. The SMS must be 100% correlated with a successful DB commit. `AFTER_COMMIT` makes that guarantee at the framework level — no manual try/catch needed.

**Schema tradeoff — OvertimeEntry separate from AttendanceLog:**
Overtime entries are the unit of settlement. Keeping them separate means settlement queries don't touch attendance records, settlement status is indexed independently, and the two concerns (tracking time vs settling money) don't interfere.

---

## AI Tools Used
- **Claude (Anthropic)** — architecture design, entity and service code, Redis strategy, ticket root cause analysis
- Used for: generating boilerplate, reviewing business logic edge cases (60h cap, 16h flag), explaining Spring `@Transactional` proxy trap

All code has been reviewed, understood, and can be explained and modified in the interview.

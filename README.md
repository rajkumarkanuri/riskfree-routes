# Risk Free Routes

**Final Year Diploma CSE Project**  
A native Android app that recommends **safer travel routes**, not just the fastest ones — powered by a rule-based safety score algorithm.

---

## Project Structure

```
riskfree-routes/
├── android/          ← Android app (Java, Material Design 3, MVVM)
├── backend/          ← Spring Boot REST API (Java 17, JWT, JPA)
└── database/         ← PostgreSQL schema and seed data
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Android | Java, XML, Material Design 3, MVVM |
| Networking | Retrofit 2 + OkHttp + Gson |
| Maps | Google Maps SDK, Places API, Directions API |
| Backend | Spring Boot 3, Spring Security, JWT |
| ORM | Hibernate + JPA |
| Database | PostgreSQL 15+ |
| Images | Cloudinary |

---

## Setup Instructions

### 1. Database Setup
```sql
-- In pgAdmin or psql:
CREATE DATABASE riskfreeroutes_db;
-- Then run: database/schema.sql
```

### 2. Backend Setup
```bash
cd backend
# Edit src/main/resources/application.properties:
#   - spring.datasource.password = your PostgreSQL password
#   - cloudinary.* = your Cloudinary credentials
#   - jwt.secret = your secret key

mvn spring-boot:run
# Server starts at: http://localhost:8080
```

### 3. Android Setup
```
1. Open android/ folder in Android Studio
2. In app/src/main/AndroidManifest.xml:
   Replace YOUR_GOOGLE_MAPS_API_KEY_HERE with your Google Maps API key
3. In utils/Constants.java:
   Change BASE_URL to your PC's IP address if testing on a real device
   (e.g., http://192.168.1.100:8080/)
4. Run on emulator or Android device (API 26+)
```

---

## Module Build Status

| # | Module | Status |
|---|---|---|
| 1 | Project Setup | ✅ Complete |
| 2 | Auth Module | 🔄 Next |
| 3 | Home + Maps | ⏳ Pending |
| 4 | Safety Score Engine | ⏳ Pending |
| 5 | Route Navigation | ⏳ Pending |
| 6 | Incident Reports | ⏳ Pending |
| 7 | Emergency Contacts | ⏳ Pending |
| 8 | SOS Module | ⏳ Pending |
| 9 | Profile | ⏳ Pending |
| 10 | Polish + Integration | ⏳ Pending |

---

## Package Name
`com.riskfreeroutes.app`

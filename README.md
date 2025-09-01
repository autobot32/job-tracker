# 📧 Email Job Tracker

> [!WARNING]
> This project is under **active development**. Features may be incomplete, buggy, or subject to change. Use at your own risk.

---

## Overview

**Email Job Tracker** is a Spring Boot + PostgreSQL application that helps you automatically track job applications straight from your Gmail inbox.  

- Connect your Gmail account with **Google OAuth2**  
- Fetch and store job-related emails in a structured database  
- Use an **LLM parser** (coming soon) to extract details like:  
  - Company  
  - Role / Title  
  - Status (applied, interview, offer, etc.)  
  - Next steps and deadlines  
  - Notes and referrals  

The goal is to give job seekers a clean dashboard — no more digging through inboxes or messy spreadsheets.

---

## Project Status

### ✅ Working
- Google OAuth2 login  
- Gmail API integration (read-only)  
- Email fetching + persistence  
- PostgreSQL via Docker Compose  
- Flyway migrations  
- Entities: Users, Emails, Applications, Tasks, Contacts  

### 🔄 In Progress
- LLM-powered email parsing  
- Application upsert logic (deduplication + status updates)  
- Basic dashboard UI (Thymeleaf)  

### 🧠 Planned
- Google Sheets export  
- React frontend  
- Application analytics & insights  
- Task management (follow-ups, interviews, etc.)  

---

## 🛠 Tech Stack

- **Language:** Java 21, TypeScript ~5.8.3 
- **Framework:** Spring Boot 3  
- **Database:** PostgreSQL (Dockerized)  
- **Migrations:** Flyway  
- **Auth & APIs:** Google OAuth2, Gmail API  
- **UI:** React (Vite), Thymeleaf   

---

## 📂 Project Structure

- `auth/` → Authentication entities & repositories (`User`, `OAuthToken`, `UserRepository`, `OAuthTokenRepository`)  
- `config/` → Configuration beans (`GmailConfig`)  
- `controller/` → Web controllers (`ApplicationController`, `DashboardController`, `HealthController`, `IngestController`)  
- `domain/` → Core domain entities (`Application`, `Email`)  
- `gmail/` → Gmail DTOs (`GmailMessage`)  
- `repo/` → Spring Data JPA repositories (`ApplicationRepository`, `EmailRepository`)  
- `security/` → Security setup (`SecurityConfig`, `CustomOAuth2SuccessHandler`)  
- `service/` → Business logic (`GmailService`, `CandidateEmailService`, `LlmClient`)  
- `utils/` → Application bootstrap (`EmailJobTrackerApplication`)  
- `resources/db/migration/` → Flyway SQL migrations (`V1__init.sql`, `V2__add_gmail_id_internal_date.sql`, `V3__rename_due_date_add_trigger.sql`)  
- `resources/static/` → Static assets (`index.html`)  
- `resources/templates/` → Thymeleaf templates (`application.yml` for config)  

---

## 🚀 Getting Started

1. **Clone the repo**  
   ```bash
   git clone https://github.com/autobot32/job-tracker.git
   cd job-tracker

2. **Run PostgreSQL via Docker**  
   ```bash
   docker-compose up -d
3. **Configure Google Console and OAuth2**
   - Set up a project in [Google Cloud Console](https://console.cloud.google.com)
   - Enable the **Gmail API**
   - Configure the **OAuth consent screen**
   - Create OAuth2 credentials (Client ID & Secret)
   - Add your credentials to `application.properties`

5. **Build and Run**  
   ```bash
   ./mvnw spring-boot:run


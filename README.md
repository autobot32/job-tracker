# 📧 Email Job Tracker

⚠️ **Work In Progress**  
This project is currently under active development. Features may be incomplete or buggy, and the app is not yet ready for production use.  

---

## 🔍 What Is This?

Email Job Tracker is a backend application that connects to your Gmail inbox, fetches recent emails, and (eventually) uses an LLM to extract job application data — such as company names, positions, deadlines, and contact info — and stores it in a structured database.  

It aims to give users a simple dashboard to track their job hunt without relying on messy spreadsheets or buried emails.  

---

## 🚧 Project Scope

### ✅ Core Features (Working)
- Google OAuth2 login  
- Gmail API integration (read-only)  
- Email fetching  
- PostgreSQL integration via Docker  
- Entities for Users, Emails, Applications, Tasks, and Contacts  

### 🔄 In Progress
- LLM parsing of emails  
- Application tracking UI  
- Task management and contact linking  

### 🧠 Planned
- Google Sheets export  
- React frontend  
- Analytics on job application progress  

---

## 🛠 Tech Stack
- Java 21  
- Spring Boot  
- PostgreSQL  
- Flyway  
- Gmail API  
- OAuth2  

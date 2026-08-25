# Vanter/Ember

[![Java](https://img.shields.io/badge/Java-17%2B-orange?style=flat-square&logo=openjdk)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18%2B-blue?style=flat-square&logo=react)](https://reactjs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-blue?style=flat-square&logo=typescript)](https://www.typescriptlib.com/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-3.x-38B2AC?style=flat-square&logo=tailwind-css)](https://tailwindcss.com/)

**EMBER** is a modern, full-stack restaurant management platform engineered to streamline dining table operations, active session tracking, and seamless customer onboarding through dynamic QR codes and secure access codes.

---

## 🚀 Key Features

* **Table Session Lifecycle Management:** Open, monitor, bill, and close dining sessions in real time with automated status updates.
* **Instant QR & Join Code Generation:** Automated creation of unique session tokens and short alphanumeric codes (`joinCodes`) enabling fast customer entry.
* **Role-Based Access Control (RBAC):** Secure endpoints and UI views tailored for staff members, specifically optimized for waiters (`WAITER`) and administrative roles.
* **Optimized API Contracts:** Strict DTO architecture paired with automated OpenAPI/Swagger specification generation to keep frontend and backend types fully synchronized.
* **Modern State & Data Flow:** Clean separation of server state (via TanStack Query) and local UI states, supported by a modular Zustand store architecture.

---

## 🛠️ Tech Stack

### Backend
* **Language & Framework:** Java, Spring Boot, Spring Security
* **Database:** PostgreSQL
* **API Documentation:** OpenAPI / Swagger UI

### Frontend
* **Library:** React with TypeScript
* **Styling & UI:** Tailwind CSS, Shadcn UI components, Lucide Icons
* **State Management:** Zustand
* **Data Fetching:** TanStack Query (React Query), Axios

---

## 📂 Architecture Overview

Vanter follows a clean layered architecture designed for scalability and maintainability:

1. **Backend Layer:** 
   * **Controllers:** Handle HTTP requests and map entities strictly to lightweight response DTOs for optimal network performance.
   * **Services:** Encapsulate core business logic and transactional integrity.
   * **Repositories:** Manage data persistence.
2. **Frontend Layer:** 
   * **Services:** Typed API communication modules leveraging auto-generated backend schemas.
   * **Components:** Reusable UI components structured with a heavy focus on UX/UI responsiveness and clean component lifecycles.

---

## ⚙️ Getting Started

### Prerequisites
Ensure you have the following installed on your machine:
* Java JDK 17 or higher
* Node.js (v18+ recommended)
* Maven or Gradle
* Docker (optional, for containerized services)

### Running the Backend
```bash
# Clone the repository
git clone [https://github.com/your-username/vanter.git](https://github.com/your-username/vanter.git)

# Navigate to the backend directory
cd vanter/backend

# Run the Spring Boot application
./mvnw spring-boot:run

# DocuSense UI: Secured Enterprise RAG Frontend Portal

[![React](https://img.shields.io/badge/React-18.3-cyan.svg)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-5.0-purple.svg)](https://vitejs.dev/)
[![TailwindCSS](https://img.shields.io/badge/TailwindCSS-v3.4-blue.svg)](https://tailwindcss.com/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

DocuSense UI is the modern, high-performance web portal for **DocuSense**—a security-first Retrieval-Augmented Generation (RAG) platform. 

It is built as a single-page application (SPA) using **React**, **Vite**, and **TailwindCSS**, styled with a premium dark-slate glassmorphic design system. The client integrates custom authorization workflows, multi-turn chat interaction, document ingestion management, and a real-time observability telemetry dashboard.

---

## 🎨 Design System & Aesthetics
* **Glassmorphism**: UI panels leverage semi-transparent backdrops (`backdrop-blur-md`), subtle radial glow effects, and thin slate borders (`border-slate-800`) to create a floating premium feel.
* **Micro-Animations**: Uses smooth transition curves (`transition-all duration-300`) and fading keyframe animations for elements like search banners, citation expansions, and navigation tabs.
* **Typography**: Clean, high-legibility sans-serif typeface matching corporate dashboard standardizations.

---

## ⚡ Key Dashboard Views & Features

### 🔐 1. Scope-Aware Authentication
* Supports **Sign In** and **Registration** forms.
* Assigns users to target departments (`Engineering`, `HR`, `Finance`, `General`) and Role Clearances (`User`, `Manager`, `Admin`) at signup.
* Decodes claims directly from the JWT client-side to dynamically hide/show privileged viewports.

### 💬 2. Secure Conversational Search (RAG Interface)
* **Typewriter Animation**: Natural text stream output utilizing dynamic speed curves.
* **collapsible Citations**: Matches vector chunks to source records and renders citation cards displaying title, chunk snippet, and percentage similarity matching.
* **RLHF Rating System**: Thumbs-up/down ratings linked directly to database satisfaction tables.
* **Ungrounded Fact Guardrail**: Features a custom validation alert banner styled in glassmorphic amber/orange that triggers if the backend flags LLM output as ungrounded (hallucinated).

### 📁 3. Document Ingestion Portal
* *Restricted to: `ROLE_MANAGER`, `ROLE_ADMIN`*
* Supports drag-and-drop or select uploads for PDFs, DOCX, and TXT files.
* Allows administrators to tag documents with specific **department scopes** and **access roles** prior to vectorization.

### 📊 4. System Telemetry & Observability
* *Restricted to: `ROLE_MANAGER`, `ROLE_ADMIN`*
* **Metrics Cards**: Displays Total LLM Costs, Cache Hit Rate, Helpful Rate (RLHF), and Average Latency.
* **Paginated Telemetry Log**: Browse live queries with search-by-username filters.
* **Paginated Satisfaction Audit**: Read exact queries alongside generated answers and user thumbs status.

---

## 📂 Frontend Directory Structure

```
DocuSenseUi/
├── src/
│   ├── components/
│   │   ├── Auth.jsx                   # Sign In / Register Glassmorphic Component
│   │   ├── SearchRag.jsx              # Secured RAG Chat & Citations Component
│   │   ├── UploadDoc.jsx              # Document Upload Portal
│   │   ├── ManageDocs.jsx             # Grid to audit & delete uploaded documents
│   │   └── ObservabilityDashboard.jsx # System Telemetry Metrics & Paginated Audit Logs
│   │
│   ├── utils/
│   │   └── api.js                     # Unified Fetch REST API client
│   │
│   ├── App.jsx                        # Main Application Layout, State & Navigation Router
│   ├── index.css                      # Tailwind Directives & Custom Global Styles
│   └── main.jsx                       # React DOM Entrypoint
│
├── public/                            # Static asset folder
├── vite.config.js                     # Vite build and server settings
├── tailwind.config.js                 # Tailwind design theme overrides
├── package.json                       # Core npm dependencies & scripts
└── eslint.config.js                   # JavaScript Lint configurations
```

---

## 🛠️ Local Installation & Development

### Prerequisites
* **Node.js** (v18.0.0 or higher)
* **npm** (v9.0.0 or higher)

### Setup Steps

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/your-username/docusense-ui.git
   cd docusense-ui
   ```

2. **Install Project Dependencies:**
   ```bash
   npm install
   ```

3. **Configure Environment Variables:**
   Create a `.env` (or `.env.local`) file in the root directory:
   ```env
   VITE_API_BASE_URL=http://localhost:8080
   ```

4. **Start the Development Server:**
   ```bash
   npm run dev
   ```
   The portal will compile and start running on: **`http://localhost:5173`**

5. **Build for Production:**
   To bundle the assets into static HTML/CSS/JS (dist folder):
   ```bash
   npm run build
   ```

---

## 🛡️ API Client Resiliency & Session Handling
The API client helper inside `src/utils/api.js` is structured as a robust REST interface:
* **Authorization Headers**: Extracts and stores session tokens inside `sessionStorage`, automatically appending the token to every outgoing request.
* **Session Expiry Hook**: Automatically dispatches a global `'auth_session_expired'` event if an API request encounters a `401 Unauthorized` or `403 Forbidden` response, immediately signing the user out and redirecting them to the authentication screen.

---

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.
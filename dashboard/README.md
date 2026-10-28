# React dashboard

A React + TypeScript version of the live dashboard. It reads the same API as the plain page at
`http://localhost:8080/`, so you can learn React without touching the Java code.

## Set up

1. Install **Node.js (LTS)** from https://nodejs.org. React itself needs no separate install: it is
   downloaded by `npm install` as a dependency of this folder.
2. Check it worked, in a new terminal:
   ```
   node -v
   npm -v
   ```
3. Install this project's packages (once):
   ```
   cd dashboard
   npm install
   ```

**On Windows PowerShell**, if `npm` fails with "running scripts is disabled on this system", either use
Git Bash instead, or run this once and retry:
```
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

## Run it while you develop

1. Start Kafka and the Spring Boot app as usual (`docker compose up -d`, then `mvn spring-boot:run`).
2. In a second terminal:
   ```
   cd dashboard
   npm run dev
   ```
3. Open **http://localhost:5173/app/**. Edits to files in `src/` show up in the browser immediately.

The dev server forwards `/api` calls to the Spring app on port 8080 (see `vite.config.ts`), so you do
not hit cross-origin errors.

## Serve it from Spring Boot

```
npm run build
```

This writes the finished site into `src/main/resources/static/app/`. Restart the Spring app and open
**http://localhost:8080/app/index.html**. The older plain page stays at `http://localhost:8080/`, so you
can compare the two.

## How the code is organised

| File | What it does |
|---|---|
| `src/main.tsx` | Starts React and mounts `<App />` into the page |
| `src/App.tsx` | The page layout; fetches the data and passes it down to components |
| `src/api.ts`, `src/types.ts` | Calls to the Spring API, and the TypeScript shapes of its JSON |
| `src/hooks/usePolling.ts` | A custom hook that fetches on a timer |
| `src/hooks/useRate.ts` | A custom hook that turns a growing counter into "per second" |
| `src/components/*` | Small pieces of the page: tiles, partition bars, event feed, send form, profile lookup |
| `src/format.ts` | Number, money and time formatting helpers |

Read them in this order: `main.tsx`, `App.tsx`, `hooks/usePolling.ts`, then the components.

## What the page does

The page is written for people who have never heard of Kafka or Ignite, and has a **Plain English / Technical terms**
switch in the top bar that swaps the wording everywhere (the choice is remembered in the browser).

- A headline and short explanation, then **Try it** buttons that send made-up updates (`SendControls.tsx`).
  **Start live stream** keeps sending a small batch every half second.
- **Watch it work**: a diagram where dots travel through the lanes while updates are being filed (`FlowDiagram.tsx`).
- **How it works, step by step**: four plain explanations, each with an "Under the hood" tag (`HowItWorks.tsx`).
- Live numbers with rolling counters, a 60 second speed chart and per-lane work bars.
- **What the credit files add up to** and **People to watch**, from the SQL endpoint `/api/analytics` (`PortfolioPanel.tsx`).
- Most active people, a live credit file for one person, and the latest updates. Clicking any person opens their file.
- **What if something goes wrong?**: the restart, duplicate and failure protections with live counters (`SafetyNets.tsx`).
- A glossary (`Glossary.tsx`) and a light/dark theme toggle.

The wording switch lives in `view.tsx`: a React context holds the choice, and components read it with `useView()`.

## Practice tasks

Read the components first, then try changing them. Ideas, easiest first:

1. Change the live stream to 100 events every 250 ms and add a number input for the batch size.
2. Add a consumer-threads panel: from the recent events, count how many each `consumer-N` handled
   (use `reduce`, and derive it during render instead of storing it in state).
3. Make the chart 5 minutes long instead of 60 seconds (`useHistory.ts`, `ThroughputChart.tsx`).
4. Add tests with Vitest and React Testing Library for `format.ts` and `StatTile`.
5. Add a Spring endpoint that streams updates with Server-Sent Events (`SseEmitter`) and a
   `useEventSource` hook that replaces polling.

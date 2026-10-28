import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev: `npm run dev` serves the page on http://localhost:5173/app/ and forwards /api calls to the
// Spring Boot app on port 8080, so the browser never sees a cross-origin request.
// Build: `npm run build` writes the finished site into Spring Boot's static folder, so the Java app
// serves it at http://localhost:8080/app/index.html.
export default defineConfig({
  base: '/app/',
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    outDir: '../src/main/resources/static/app',
    emptyOutDir: true,
  },
});

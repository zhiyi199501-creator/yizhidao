import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/admin/",
  plugins: [
    react(),
    {
      name: "redirect-admin-slash",
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url === "/admin") {
            res.statusCode = 302;
            res.setHeader("Location", "/admin/");
            res.end();
            return;
          }
          next();
        });
      },
    },
  ],
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/v1": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    },
  },
});

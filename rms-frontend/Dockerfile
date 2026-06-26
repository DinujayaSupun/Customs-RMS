# =====================================================================
# Stage 1 — BUILD: turn the Vue source into static HTML/CSS/JS files
# =====================================================================
FROM node:20-alpine AS build
WORKDIR /app

# Copy only the dependency manifests first, then install.
# Like the backend, this layer is cached unless package*.json changes.
COPY package.json package-lock.json ./
RUN npm ci

# IMPORTANT: Vite "bakes in" environment variables at BUILD time, not run
# time. So the API URL the browser will call must be known here. We accept
# it as a build argument (set in docker-compose.yml) with a sensible default.
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

# Copy the rest of the source and produce the static site into /app/dist
COPY . .
RUN npm run build

# =====================================================================
# Stage 2 — SERVE: a tiny Nginx web server that hands out those files
# =====================================================================
FROM nginx:alpine

# Replace Nginx's default site config with ours (handles Vue routing)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the built static files from Stage 1 into Nginx's web root
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

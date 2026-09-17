# Build: genera el bundle estático de producción.
FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Runtime: nginx sirve el bundle y hace de reverse proxy hacia el backend, de modo
# que el navegador (incluido el del móvil) solo habla con un único origen — nginx.conf
# reenvía /api y /ws al servicio "backend" por la red interna de Docker.
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# VITE_API_BASE_URL is baked into the JS bundle at build time (import.meta.env).
# Railway supplies it via the "Build Variables" panel for the web service.
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN case "$VITE_API_BASE_URL" in \
			http://*|https://*) ;; \
			*) echo "VITE_API_BASE_URL must be an absolute http(s) URL" >&2; exit 1 ;; \
		esac
RUN npm run build

FROM nginx:alpine
# gettext provides envsubst, used below to stamp $PORT into the nginx template.
RUN apk add --no-cache gettext
COPY --from=build /app/dist /usr/share/nginx/html
# nginx.conf.template uses ${PORT} so Railway (or any PaaS) can inject its
# dynamic port without rebuilding the image.
COPY nginx.conf /etc/nginx/conf.d/default.conf.template
# Substitute runtime env vars into the nginx config, then start nginx in the
# foreground (daemon off required for the container to stay alive).
CMD ["/bin/sh", "-c", "PORT=${PORT:-3000}; export PORT; envsubst '${PORT}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf && exec nginx -g 'daemon off;'"]
EXPOSE 3000

FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
# Empty by default: the production image is single-origin (nginx below proxies /api/** to the
# backends), so the bundle should call relative paths, not a build-time-baked absolute URL that
# would point every remote browser at ITS OWN localhost. Set these only for a standalone build
# that talks directly to a backend with no reverse proxy in front of it.
ARG VITE_BACKEND_URL=
ARG VITE_INGESTOR_URL=
ENV VITE_BACKEND_URL=$VITE_BACKEND_URL
ENV VITE_INGESTOR_URL=$VITE_INGESTOR_URL
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY templates/default.conf.template /etc/nginx/templates/default.conf.template
# Matches the docker-compose service names; override for k8s (Service names differ there).
ENV BOT_UPSTREAM=documentation-bot:8082
ENV INGESTOR_UPSTREAM=document-ingestor:8081
EXPOSE 80

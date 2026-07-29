FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
# Base path the app is served under (routing, asset URLs, and the nginx location below all
# follow it). No trailing slash; must be a non-root path like /docs-inator.
ARG BASE_PATH=/docs-inator
ENV VITE_BASE_PATH=$BASE_PATH
# Empty by default: the production image is single-origin (nginx below proxies /api/** to the
# backends), so the bundle should call relative paths, not a build-time-baked absolute URL that
# would point every remote browser at ITS OWN localhost. Set these only for a standalone build
# that talks directly to a backend with no reverse proxy in front of it.
ARG VITE_BACKEND_URL=
ARG VITE_INGESTOR_URL=
ENV VITE_BACKEND_URL=$VITE_BACKEND_URL
ENV VITE_INGESTOR_URL=$VITE_INGESTOR_URL
# Optional branding overrides; empty means "use the defaults from src/config/app.json".
ARG VITE_APP_TITLE=
ARG VITE_APP_SUBTITLE=
ARG VITE_APP_DESCRIPTION=
ARG VITE_THEME_COLOR=
ARG VITE_MAX_MESSAGE_LENGTH=
ENV VITE_APP_TITLE=$VITE_APP_TITLE
ENV VITE_APP_SUBTITLE=$VITE_APP_SUBTITLE
ENV VITE_APP_DESCRIPTION=$VITE_APP_DESCRIPTION
ENV VITE_THEME_COLOR=$VITE_THEME_COLOR
ENV VITE_MAX_MESSAGE_LENGTH=$VITE_MAX_MESSAGE_LENGTH
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY templates/default.conf.template /etc/nginx/templates/default.conf.template
# Matches the docker-compose service names; override for k8s (Service names differ there).
ENV BOT_UPSTREAM=documentation-bot:8080
ENV INGESTOR_UPSTREAM=document-ingestor:8080
# Baked from the same build arg as the bundle so nginx's redirect/alias locations agree with the
# JS routing. Overriding BASE_PATH at *runtime* alone won't work — the bundle's base path is
# fixed at build time; rebuild with --build-arg BASE_PATH=... instead.
ARG BASE_PATH=/docs-inator
ENV BASE_PATH=$BASE_PATH
EXPOSE 80
